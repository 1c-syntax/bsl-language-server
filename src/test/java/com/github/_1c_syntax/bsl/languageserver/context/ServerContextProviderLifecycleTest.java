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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceBeanScope;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ServerContextProviderLifecycleTest {

  @Test
  void removalKeepsWorkspaceAvailableUntilPreparationHandlerCompletes(@TempDir Path root) {
    var context = mock(ServerContext.class);
    var scope = mock(WorkspaceBeanScope.class);
    var beans = new StaticListableBeanFactory();
    beans.addBean("serverContext", context);
    var provider = new ServerContextProvider(beans.getBeanProvider(ServerContext.class),
      mock(LanguageServerConfiguration.class), scope);
    var uri = Absolute.uri(root.toUri());
    var folder = new WorkspaceFolder(uri.toString(), "preparing");
    provider.addWorkspace(folder);
    try {
      doAnswer(invocation -> {
        // Registration may start after removal has begun and resolve its context then.
        var preparing = provider.getAllContexts().get(uri);
        assertThat(preparing).isSameAs(context);
        verify(context, never()).clear();
        verify(scope, never()).removeWorkspace(uri);
        preparing.finishPreparation();
        return null;
      }).when(context).awaitPreparation();

      provider.removeWorkspace(folder);

      assertThat(provider.getAllContexts()).doesNotContainKey(uri);
      var order = inOrder(context, scope);
      order.verify(context).awaitPreparation();
      order.verify(context).finishPreparation();
      order.verify(context).clear();
      order.verify(scope).removeWorkspace(uri);
    } finally {
      WorkspaceContextHolder.unregisterWorkspace(uri);
    }
  }
  @Test
  void registrationWaitsUntilPreviousWorkspaceScopeIsRemoved(@TempDir Path root) throws Exception {
    var previous = mock(ServerContext.class);
    var replacement = mock(ServerContext.class);
    var scope = mock(WorkspaceBeanScope.class);
    var beans = new StaticListableBeanFactory();
    beans.addBean("serverContext", previous);
    var provider = new ServerContextProvider(beans.getBeanProvider(ServerContext.class),
      mock(LanguageServerConfiguration.class), scope);
    var uri = Absolute.uri(root.toUri());
    var folder = new WorkspaceFolder(uri.toString(), "reopened");
    provider.addWorkspace(folder);
    var cleanupStarted = new CountDownLatch(1);
    var releaseCleanup = new CountDownLatch(1);
    var additionThread = new AtomicReference<Thread>();
    var executor = Executors.newFixedThreadPool(2);
    try {
      doAnswer(invocation -> {
        cleanupStarted.countDown();
        assertThat(releaseCleanup.await(5, TimeUnit.SECONDS)).isTrue();
        return null;
      }).when(scope).removeWorkspace(uri);
      var removal = executor.submit(() -> provider.removeWorkspace(folder));
      assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
      beans.addBean("serverContext", replacement);
      var addition = executor.submit(() -> {
        additionThread.set(Thread.currentThread());
        return provider.addWorkspace(folder);
      });
      await().atMost(Duration.ofSeconds(5)).until(() ->
        addition.isDone() || (additionThread.get() != null
          && additionThread.get().getState() == Thread.State.BLOCKED));

      assertThat(addition.isDone()).isFalse();
      releaseCleanup.countDown();
      removal.get(5, TimeUnit.SECONDS);
      assertThat(addition.get(5, TimeUnit.SECONDS)).isSameAs(replacement);
      assertThat(provider.getAllContexts()).containsEntry(uri, replacement);
      verify(replacement, never()).clear();
      verify(scope).removeWorkspace(uri);
    } finally {
      releaseCleanup.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      WorkspaceContextHolder.unregisterWorkspace(uri);
    }
  }

  @Test
  void concurrentRemovalsPrepareAndClearTheWorkspaceOnlyOnce(@TempDir Path root) throws Exception {
    var context = mock(ServerContext.class);
    var scope = mock(WorkspaceBeanScope.class);
    var beans = new StaticListableBeanFactory();
    beans.addBean("serverContext", context);
    var provider = new ServerContextProvider(beans.getBeanProvider(ServerContext.class),
      mock(LanguageServerConfiguration.class), scope);
    var uri = Absolute.uri(root.toUri());
    var folder = new WorkspaceFolder(uri.toString(), "removed-twice");
    provider.addWorkspace(folder);
    var preparationStarted = new CountDownLatch(1);
    var releasePreparation = new CountDownLatch(1);
    var preparationCalls = new AtomicInteger();
    var secondThread = new AtomicReference<Thread>();
    var executor = Executors.newFixedThreadPool(2);
    try {
      doAnswer(invocation -> {
        preparationCalls.incrementAndGet();
        preparationStarted.countDown();
        assertThat(releasePreparation.await(5, TimeUnit.SECONDS)).isTrue();
        return null;
      }).when(context).awaitPreparation();
      var first = executor.submit(() -> provider.removeWorkspace(folder));
      assertThat(preparationStarted.await(5, TimeUnit.SECONDS)).isTrue();
      var second = executor.submit(() -> {
        secondThread.set(Thread.currentThread());
        provider.removeWorkspace(folder);
      });
      await().atMost(Duration.ofSeconds(5)).until(() ->
        preparationCalls.get() == 2 || (secondThread.get() != null
          && secondThread.get().getState() == Thread.State.BLOCKED));
      releasePreparation.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);

      assertThat(provider.getAllContexts()).doesNotContainKey(uri);
      verify(context).awaitPreparation();
      verify(context).clear();
      verify(scope).removeWorkspace(uri);
    } finally {
      releasePreparation.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      WorkspaceContextHolder.unregisterWorkspace(uri);
    }
  }

}
