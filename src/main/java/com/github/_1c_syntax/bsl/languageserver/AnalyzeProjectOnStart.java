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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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
  @Async
  public void handleEvent(ServerContextPopulatedEvent event) {

    if (!configuration.getDiagnosticsOptions().isAnalyzeOnStart()) {
      return;
    }

    if (!languageClientHolder.isConnected()) {
      return;
    }

    var serverContext = event.getSource();

    // Клиент с pull-моделью сам запросит диагностики (в том числе через
    // workspace/diagnostic/refresh), поэтому push-публикацию по открытым документам
    // для него выполнять не нужно — иначе возникает второй конкурирующий источник
    // тех же диагностик.
    var publishDiagnostics = !diagnosticProvider.supportsPullDiagnostics();

    var documentContexts = serverContext.getDocuments().values();
    var progress = workDoneProgressHelper.createProgress(documentContexts.size(), getMessage("filesSuffix"));
    progress.beginProgress(getMessage("analyzeProject"));

    try {
      executor.submit(() ->
        documentContexts.parallelStream().forEach((DocumentContext documentContext) -> {
          progress.tick();

          serverContext.rebuildDocument(documentContext);
          if (publishDiagnostics) {
            diagnosticProvider.computeAndPublishDiagnostics(documentContext);
          }

          serverContext.tryClearDocument(documentContext);
        })
      ).get();

      progress.endProgress(getMessage("projectAnalyzed"));
    } catch (ExecutionException e) {
      throw new IllegalStateException("Can't analyze project on start", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while analyzing project on start", e);
    }
  }

  private String getMessage(String key) {
    var language = configuration.getLanguage();
    return Resources.getResourceString(language, getClass(), key);
  }

}
