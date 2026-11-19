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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.client.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Перехватчик события заполнения контекста сервера, запускающий анализ всех файлов контекста.
 */
@Component
@RequiredArgsConstructor
public class AnalyzeProjectOnStart {

  private final DiagnosticProvider diagnosticProvider;
  private final LanguageClientHolder languageClientHolder;
  private final WorkDoneProgressHelper workDoneProgressHelper;
  private final LanguageServerConfiguration configuration;
  @Qualifier("analyzeOnStartExecutor")
  private final ExecutorService executor;

  @EventListener
  public void handleEvent(ServerContextPopulatedEvent event) {

    if (!configuration.getDiagnosticsOptions().isAnalyzeOnStart()) {
      return;
    }

    if (!languageClientHolder.isConnected()) {
      return;
    }

    var serverContext = event.getSource();
    // Анализ — фоновая работа над рабочей областью: её остановка дожидается анализа, даже
    // если слушатель отработал в своём потоке.
    serverContext.runBackgroundWork(() -> analyze(serverContext));
  }

  private void analyze(ServerContext serverContext) {
    var documentContexts = serverContext.getDocuments().values();
    var progress = workDoneProgressHelper.createProgress(documentContexts.size(), getMessage("filesSuffix"));
    progress.beginProgress(getMessage("analyzeProject"));

    var cancelled = new AtomicBoolean();
    var completed = false;
    try {
      var analysis = CompletableFuture.runAsync(
        () -> analyzeDocuments(serverContext, documentContexts, progress, cancelled), executor);
      awaitAnalysis(analysis, cancelled);
      completed = !serverContext.isBackgroundWorkStopped();
    } finally {
      progress.endProgress(completed ? getMessage("projectAnalyzed") : "");
    }
  }

  private static void awaitAnalysis(CompletableFuture<Void> analysis, AtomicBoolean cancelled) {
    try {
      analysis.get();
    } catch (ExecutionException e) {
      throw new IllegalStateException("Can't analyze project on start", e);
    } catch (InterruptedException e) {
      cancelled.set(true);
      // Отмена Future переводит её в завершённое состояние сразу. Здесь нужно дождаться
      // физических работников до возврата из runBackgroundWork и очистки контекста.
      analysis.handle((ignored, failure) -> null).join();
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while analyzing project on start", e);
    }
  }

  private void analyzeDocuments(
    ServerContext serverContext, Collection<DocumentContext> documents,
    WorkDoneProgressHelper.WorkDoneProgressReporter progress, AtomicBoolean cancelled
  ) {
    var active = new Phaser(1);
    try {
      documents.parallelStream().forEach((DocumentContext document) -> {
        if (cancelled.get() || serverContext.isBackgroundWorkStopped() || active.register() < 0) {
          return;
        }
        try {
          analyzeDocument(serverContext, document, progress, cancelled);
        } finally {
          active.arriveAndDeregister();
        }
      });
    } finally {
      // При исключении параллельный проход может выйти раньше соседних обработчиков.
      // Зарегистрированные дожидаются окончания, остальные не начинают работу.
      cancelled.set(true);
      active.awaitAdvance(active.arriveAndDeregister());
    }
  }

  private void analyzeDocument(
    ServerContext serverContext, DocumentContext document,
    WorkDoneProgressHelper.WorkDoneProgressReporter progress, AtomicBoolean cancelled
  ) {
    progress.tick();
    // Перестроение, переиндексация и очистка идут под блокировкой записи документа.
    var lock = serverContext.getDocumentLock(document.getUri()).writeLock();
    lock.lock();
    try {
      if (cancelled.get() || serverContext.isBackgroundWorkStopped()) {
        return;
      }
      serverContext.rebuildDocument(document);
      diagnosticProvider.computeAndPublishDiagnostics(document);
      serverContext.tryClearDocument(document);
    } finally {
      lock.unlock();
    }
  }

  private String getMessage(String key) {
    var language = configuration.getLanguage();
    return Resources.getResourceString(language, getClass(), key);
  }

}
