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
import com.github._1c_syntax.bsl.languageserver.util.WorkspaceContextTestExecutionListener;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentChangeExecutorTest {

  private static final URI TEST_URI = URI.create("file:///test.bsl");
  private static final URI WORKSPACE_URI = WorkspaceContextTestExecutionListener.DEFAULT_TEST_WORKSPACE;

  private DocumentContext documentContext;
  private ServerContext serverContext;
  private WorkspaceBackgroundWork backgroundWork;
  private DocumentChangeExecutor.DocumentChangeListener listener;
  private DocumentChangeExecutor executor;
  private AtomicInteger listenerCalls;

  @BeforeEach
  void setUp() {
    WorkspaceContextHolder.registerWorkspace(WORKSPACE_URI, "test-workspace");

    documentContext = mock(DocumentContext.class);
    serverContext = mock(ServerContext.class);
    when(documentContext.getContent()).thenReturn("base");
    when(documentContext.getVersion()).thenReturn(0);
    when(documentContext.getUri()).thenReturn(TEST_URI);
    when(documentContext.getServerContext()).thenReturn(serverContext);
    when(serverContext.getWorkspaceUri()).thenReturn(WORKSPACE_URI);
    backgroundWork = new WorkspaceBackgroundWork();
    when(serverContext.runBackgroundWork(any())).thenAnswer(invocation -> backgroundWork.run(invocation.getArgument(0)));
    when(serverContext.isBackgroundWorkStopped()).thenAnswer(invocation -> backgroundWork.isStopped());

    // Create a map to store locks per URI, ensuring the same lock is returned for the same URI
    Map<URI, ReadWriteLock> lockMap = new ConcurrentHashMap<>();
    when(serverContext.getDocumentLock(any())).thenAnswer(invocation -> {
      URI uri = invocation.getArgument(0);
      return lockMap.computeIfAbsent(uri, k -> new ReentrantReadWriteLock());
    });

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
    WorkspaceContextHolder.clear();
    WorkspaceContextHolder.unregisterWorkspace(WORKSPACE_URI);
  }

  private static String apply(String base, List<TextDocumentContentChangeEvent> changes) {
    String result = base;
    for (var change : changes) {
      result = change.getText();
    }
    return result;
  }

  @Test
  void stoppedWorkspaceCancelsWaitersWithoutApplyingChanges() throws Exception {
    var tryingAdmission = new CompletableFuture<Void>();
    var reject = new CompletableFuture<Void>();
    doAnswer(invocation -> {
      tryingAdmission.complete(null);
      reject.get(5, TimeUnit.SECONDS);
      return false;
    }).when(serverContext).runBackgroundWork(any());

    executor.submit(1, List.of(new TextDocumentContentChangeEvent("first")));
    CompletableFuture<Void> waiter;
    try {
      tryingAdmission.get(5, TimeUnit.SECONDS);
      waiter = executor.awaitLatest();
      assertThat(waiter).isNotDone();
    } finally {
      reject.complete(null);
    }

    assertThatThrownBy(() -> waiter.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
    assertThat(executor.awaitLatest()).isCancelled();
    assertThat(listenerCalls).hasValue(0);
  }

  @Test
  void workspaceStopWhileWaitingForTheDocumentLockSkipsTheChange() {
    var lock = new ReentrantReadWriteLock();
    when(serverContext.getDocumentLock(TEST_URI)).thenReturn(lock);

    CompletableFuture<Void> waiter;
    lock.writeLock().lock();
    try {
      executor.submit(1, List.of(new TextDocumentContentChangeEvent("first")));
      await().atMost(5, TimeUnit.SECONDS).until(lock::hasQueuedThreads);
      waiter = executor.awaitLatest();
      assertThat(waiter).isNotDone();

      backgroundWork.requestStop();
    } finally {
      lock.writeLock().unlock();
    }

    assertThatThrownBy(() -> waiter.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
    assertThat(executor.awaitLatest()).isCancelled();
    assertThat(listenerCalls).hasValue(0);
  }

  @Test
  void shutdownNowCancelsUnappliedWaitersAfterTheAdmittedChangeFinishes() throws Exception {
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    var applying = new CompletableFuture<Void>();
    var finishApplying = new CompletableFuture<Void>();
    listener = (ctx, content, version) -> {
      listenerCalls.incrementAndGet();
      applying.complete(null);
      finishApplying.join();
    };
    executor = new DocumentChangeExecutor(documentContext, DocumentChangeExecutorTest::apply, listener, "test");

    CompletableFuture<Void> applied;
    CompletableFuture<Void> discarded;
    try {
      executor.submit(1, List.of(new TextDocumentContentChangeEvent("first")));
      applying.get(5, TimeUnit.SECONDS);
      applied = executor.awaitLatest();

      executor.submit(2, List.of(new TextDocumentContentChangeEvent("discarded")));
      discarded = executor.awaitLatest();
      backgroundWork.requestStop();
      executor.shutdownNow();

      assertThat(applied).isNotDone();
      assertThat(discarded).isNotDone();
    } finally {
      finishApplying.complete(null);
    }

    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    applied.get(5, TimeUnit.SECONDS);
    assertThat(discarded).isCancelled();
    assertThat(executor.awaitLatest()).isCancelled();
    assertThat(listenerCalls).hasValue(1);
  }

  @Test
  void awaitLatestCompletesAfterChangeApplied() throws Exception {
    // Shutdown executor from setUp before creating a new one
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.SECONDS);

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

  /**
   * Проверяет, что workspace context корректно установлен в потоке-исполнителе при вызове onChange.
   * Это регрессионный тест для бага: DocumentChangeExecutor создаёт собственный поток без
   * workspace context, что приводило к ScopeNotActiveException при обращении к workspace-scoped бинам
   * (например, AnnotationRepository) внутри обработчика события rebuild.
   */
  @Test
  void flushPendingChanges_setsWorkspaceContextOnWorkerThread() throws Exception {
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.SECONDS);

    var capturedWorkspaceUri = new AtomicReference<URI>();
    var latch = new CountDownLatch(1);

    listener = (ctx, content, version) -> {
      capturedWorkspaceUri.set(WorkspaceContextHolder.get());
      latch.countDown();
    };

    executor = new DocumentChangeExecutor(
      documentContext,
      DocumentChangeExecutorTest::apply,
      listener,
      "test"
    );

    executor.submit(1, List.of(new TextDocumentContentChangeEvent("updated")));
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

    assertThat(capturedWorkspaceUri.get())
      .as("workspace context must be set on the worker thread during onChange")
      .isEqualTo(WORKSPACE_URI);
  }

  @Test
  void awaitLatestReturnsImmediatelyWhenUpToDate() throws Exception {
    assertThat(executor.awaitLatest().isDone()).isTrue();

    executor.submit(1, List.of(new TextDocumentContentChangeEvent("updated")));
    executor.awaitLatest().get(1, TimeUnit.SECONDS);
    assertThat(executor.awaitLatest().isDone()).isTrue();
  }
}
