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
import com.github._1c_syntax.bsl.languageserver.utils.NamedForkJoinWorkerThreadFactory;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

/**
 * Перехватчик события заполнения контекста сервера, запускающий анализ всех файлов контекста.
 */
@Component
@RequiredArgsConstructor
public class AnalyzeProjectOnStart {

  private final LanguageServerConfiguration configuration;
  private final DiagnosticProvider diagnosticProvider;
  private final LanguageClientHolder languageClientHolder;
  private final WorkDoneProgressHelper workDoneProgressHelper;

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

    var documentContexts = serverContext.getDocuments().values();
    var progress = workDoneProgressHelper.createProgress(documentContexts.size(), getMessage("filesSuffix"));
    progress.beginProgress(getMessage("analyzeProject"));

    var factory = new NamedForkJoinWorkerThreadFactory("analyze-on-start-");
    var executorService = new ForkJoinPool(ForkJoinPool.getCommonPoolParallelism(), factory, null, true);

    try {
      executorService.submit(() ->
        documentContexts.parallelStream().forEach((DocumentContext documentContext) -> {
          progress.tick();

          serverContext.rebuildDocument(documentContext);
          diagnosticProvider.computeAndPublishDiagnostics(documentContext);

          serverContext.tryClearDocument(documentContext);
        })
      ).get();

    } catch (ExecutionException e) {
      throw new RuntimeException("Can't analyze project on start", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while analyzing project on start", e);
    } finally {
      executorService.shutdown();
    }

    progress.endProgress(getMessage("projectAnalyzed"));
  }

  private String getMessage(String key) {
    return Resources.getResourceString(configuration.getLanguage(), getClass(), key);
  }

}
