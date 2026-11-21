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
package com.github._1c_syntax.bsl.languageserver.lsp;

import com.github._1c_syntax.bsl.languageserver.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.client.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyzeProjectOnStartStoppingTest {

  private ExecutorService executor;
  private ServerContext context;
  private DocumentContext document;
  private DiagnosticProvider diagnostics;
  private WorkDoneProgressHelper.WorkDoneProgressReporter progress;
  private AnalyzeProjectOnStart analyzer;

  @BeforeEach
  void prepare() {
    executor = new ForkJoinPool(2);
    context = spy(new ServerContext(new StaticListableBeanFactory().getBeanProvider(DocumentContext.class),
      mock(WorkDoneProgressHelper.class), mock(GlobalLanguageServerConfiguration.class),
      executor, executor, Caffeine.newBuilder().build()));
    document = mock(DocumentContext.class);
    when(document.getUri()).thenReturn(URI.create("file:///analysis-stop.bsl"));
    doReturn(Map.of(document.getUri(), document)).when(context).getDocuments();
    doNothing().when(context).rebuildDocument(document);
    doReturn(true).when(context).tryClearDocument(document);

    diagnostics = mock(DiagnosticProvider.class);
    progress = mock(WorkDoneProgressHelper.WorkDoneProgressReporter.class);
    var helper = mock(WorkDoneProgressHelper.class);
    when(helper.createProgress(anyInt(), anyString())).thenReturn(progress);
    var client = mock(LanguageClientHolder.class);
    when(client.isConnected()).thenReturn(true);
    var configuration = mock(LanguageServerConfiguration.class, RETURNS_DEEP_STUBS);
    when(configuration.getDiagnosticsOptions().isAnalyzeOnStart()).thenReturn(true);
    when(configuration.getLanguage()).thenReturn(Language.RU);
    analyzer = new AnalyzeProjectOnStart(diagnostics, client, helper, configuration, executor);
  }

  @AfterEach
  void stopExecutor() {
    executor.shutdownNow();
  }

  @Test
  void completedAnalysisReportsSuccess() {
    analyzer.handleEvent(new ServerContextPopulatedEvent(context));

    verify(diagnostics).computeAndPublishDiagnostics(document);
    var message = ArgumentCaptor.forClass(String.class);
    verify(progress).endProgress(message.capture());
    assertThat(message.getValue()).isNotBlank();
  }

  @Test
  void stoppingWhileDocumentLockIsHeldSkipsAnalysisAndSuccessMessage() throws Exception {
    var lock = (ReentrantReadWriteLock) context.getDocumentLock(document.getUri());
    lock.writeLock().lock();
    try (var controls = Executors.newFixedThreadPool(2)) {
      var analysis = controls.submit(() -> analyzer.handleEvent(new ServerContextPopulatedEvent(context)));
      try {
        await().atMost(10, SECONDS).until(lock::hasQueuedThreads);
        var stopping = controls.submit(context::stopBackgroundWork);
        await().atMost(10, SECONDS).until(context::isBackgroundWorkStopped);
        lock.writeLock().unlock();
        analysis.get(10, SECONDS);
        stopping.get(10, SECONDS);
      } finally {
        if (lock.isWriteLockedByCurrentThread()) {
          lock.writeLock().unlock();
        }
      }
    }

    verify(context, never()).rebuildDocument(document);
    verify(diagnostics, never()).computeAndPublishDiagnostics(document);
    verify(progress).endProgress("");
  }

  @Test
  void interruptionDoesNotReleaseTheWorkspaceWhileADiagnosticStillRuns() throws Exception {
    var workerEntered = new CountDownLatch(1);
    var finishWorker = new CountDownLatch(1);
    doAnswer(invocation -> {
      workerEntered.countDown();
      assertThat(finishWorker.await(10, SECONDS)).isTrue();
      return null;
    }).when(diagnostics).computeAndPublishDiagnostics(document);
    var listener = new AtomicReference<Thread>();

    try (var controls = Executors.newFixedThreadPool(2)) {
      var analysis = controls.submit(() -> {
        listener.set(Thread.currentThread());
        try {
          analyzer.handleEvent(new ServerContextPopulatedEvent(context));
          return false;
        } catch (IllegalStateException e) {
          return Thread.currentThread().isInterrupted() && e.getCause() instanceof InterruptedException;
        }
      });
      try {
        assertThat(workerEntered.await(5, SECONDS)).isTrue();
        listener.get().interrupt();
        var stopping = controls.submit(context::stopBackgroundWork);
        await().atMost(5, SECONDS).until(context::isBackgroundWorkStopped);
        await().pollInterval(Duration.ofMillis(20)).during(Duration.ofMillis(150)).atMost(3, SECONDS)
          .until(() -> !stopping.isDone() && !analysis.isDone());
        verify(progress, never()).endProgress(anyString());

        finishWorker.countDown();
        assertThat(analysis.get(5, SECONDS)).isTrue();
        stopping.get(5, SECONDS);
      } finally {
        finishWorker.countDown();
      }
    }
    verify(progress).endProgress("");
  }

  @Test
  void aFailedDiagnosticDoesNotReleaseTheWorkspaceWhileAnotherFileStillRuns() throws Exception {
    var broken = mock(DocumentContext.class);
    when(broken.getUri()).thenReturn(URI.create("file:///analysis-failure.bsl"));
    var documents = new LinkedHashMap<URI, DocumentContext>();
    documents.put(document.getUri(), document);
    documents.put(broken.getUri(), broken);
    doReturn(documents).when(context).getDocuments();
    doNothing().when(context).rebuildDocument(broken);
    var workerEntered = new CountDownLatch(1);
    var workerFailed = new CountDownLatch(1);
    var finishWorker = new CountDownLatch(1);
    doAnswer(invocation -> {
      workerEntered.countDown();
      assertThat(finishWorker.await(10, SECONDS)).isTrue();
      return null;
    }).when(diagnostics).computeAndPublishDiagnostics(document);
    var failure = new IllegalArgumentException("Diagnostic failed");
    doAnswer(invocation -> {
      assertThat(workerEntered.await(5, SECONDS)).isTrue();
      workerFailed.countDown();
      throw failure;
    }).when(diagnostics).computeAndPublishDiagnostics(broken);

    try (var controls = Executors.newFixedThreadPool(2)) {
      var analysis = controls.submit(() -> catchThrowable(
        () -> analyzer.handleEvent(new ServerContextPopulatedEvent(context))));
      try {
        assertThat(workerFailed.await(5, SECONDS)).isTrue();
        var stopping = controls.submit(context::stopBackgroundWork);
        await().atMost(5, SECONDS).until(context::isBackgroundWorkStopped);
        await().pollInterval(Duration.ofMillis(20)).during(Duration.ofMillis(150)).atMost(3, SECONDS)
          .until(() -> !stopping.isDone() && !analysis.isDone());
        verify(progress, never()).endProgress(anyString());

        finishWorker.countDown();
        assertThat(analysis.get(5, SECONDS)).isInstanceOf(IllegalStateException.class).hasRootCause(failure);
        stopping.get(5, SECONDS);
      } finally {
        finishWorker.countDown();
      }
    }
    verify(progress).endProgress("");
  }
}
