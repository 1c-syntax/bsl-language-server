/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentChangeExecutorTest {

  private DocumentContext documentContext;
  private DocumentChangeExecutor.DocumentChangeListener listener;
  private DocumentChangeExecutor executor;
  private AtomicInteger listenerCalls;

  @BeforeEach
  void setUp() {
    documentContext = mock(DocumentContext.class);
    when(documentContext.getContent()).thenReturn("base");
    when(documentContext.getVersion()).thenReturn(0);
    listenerCalls = new AtomicInteger();
    listener = (ctx, content, version) -> listenerCalls.incrementAndGet();
    executor = new DocumentChangeExecutor(
      documentContext,
      DocumentChangeExecutorTest::apply,
      listener,
      "test"
    );
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.SECONDS);
  }

  private static String apply(String base, List<TextDocumentContentChangeEvent> changes) {
    String result = base;
    for (var change : changes) {
      result = change.getText();
    }
    return result;
  }

  @Test
  void awaitLatestCompletesAfterChangeApplied() throws Exception {
    var change = List.of(new TextDocumentContentChangeEvent("first"));
    var latch = new CountDownLatch(1);
    listener = (ctx, content, version) -> {
      listenerCalls.incrementAndGet();
      latch.countDown();
    };

    executor = new DocumentChangeExecutor(
      documentContext,
      DocumentChangeExecutorTest::apply,
      listener,
      "test"
    );

    executor.submit(1, change);
    CompletableFuture<Void> waiter = executor.awaitLatest();

    latch.await(1, TimeUnit.SECONDS);
    waiter.get(1, TimeUnit.SECONDS);
    assertThat(listenerCalls.get()).isEqualTo(1);
  }

  @Test
  void awaitLatestReturnsImmediatelyWhenUpToDate() throws Exception {
    assertThat(executor.awaitLatest().isDone()).isTrue();

    executor.submit(1, List.of(new TextDocumentContentChangeEvent("updated")));
    executor.awaitLatest().get(1, TimeUnit.SECONDS);
    assertThat(executor.awaitLatest().isDone()).isTrue();
  }
}
