/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ServerContextContentReleaseLockTest extends AbstractServerContextAwareTest {
  private static final String SOURCE = """
    Процедура Проверка()
      Возврат;
    КонецПроцедуры
    """;

  @TempDir
  Path workspace;

  @BeforeEach
  void initWorkspace() {
    initServerContext(workspace, false);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void contentReleaseWaitsForAnActiveReader(boolean close) throws Exception {
    var document = TestUtils.getDocumentContext(workspace.resolve("Module.bsl").toUri(), SOURCE, context);
    if (close) {
      context.openDocument(document, SOURCE, 1);
    }
    var lock = (ReentrantReadWriteLock) context.getDocumentLock(document.getUri());
    var started = new CountDownLatch(1);
    var cleanupThread = new AtomicReference<Thread>();
    var uri = context.getWorkspaceUri();

    try (var cleaner = Executors.newSingleThreadExecutor()) {
      lock.readLock().lock();
      var release = cleaner.submit(() -> WorkspaceContextHolder.run(uri, () -> {
        cleanupThread.set(Thread.currentThread());
        started.countDown();
        if (close) {
          context.closeDocument(document);
        } else {
          context.tryClearDocument(document);
        }
      }));
      try {
        assertThat(started.await(5, SECONDS)).isTrue();
        await().atMost(5, SECONDS).until(() -> release.isDone() || lock.hasQueuedThread(cleanupThread.get()));

        assertThat(release.isDone()).isFalse();
        assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITH_CONTENT);
        assertThat(document.getAst()).isNotNull();
        assertThat(context.isDocumentOpened(document)).isEqualTo(close);
      } finally {
        lock.readLock().unlock();
      }

      release.get(5, SECONDS);
      assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
      assertThat(context.isDocumentOpened(document)).isFalse();
      assertThatThrownBy(document::getAst).isInstanceOf(DocumentContentUnavailableException.class);
    }
  }

  @Test
  void clearingAnOpenedDocumentDoesNotWaitForReaders() throws Exception {
    var document = TestUtils.getDocumentContext(workspace.resolve("Module.bsl").toUri(), SOURCE, context);
    context.openDocument(document, SOURCE, 1);
    var lock = context.getDocumentLock(document.getUri()).readLock();
    var uri = context.getWorkspaceUri();

    try (var cleaner = Executors.newSingleThreadExecutor()) {
      lock.lock();
      try {
        var release = cleaner.submit(() -> WorkspaceContextHolder.call(uri,
          () -> context.tryClearDocument(document)));

        assertThat(release.get(5, SECONDS)).isFalse();
        assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITH_CONTENT);
        assertThat(document.getAst()).isNotNull();
      } finally {
        lock.unlock();
      }
    }
  }
}
