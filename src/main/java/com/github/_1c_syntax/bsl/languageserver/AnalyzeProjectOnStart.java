/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalyzeProjectOnStart {

  private final LanguageServerConfiguration configuration;
  private final DiagnosticProvider diagnosticProvider;
  private final WorkDoneProgressHelper workDoneProgressHelper;

  @EventListener
  public void handleEvent(ServerContextPopulatedEvent event) {
    if (!configuration.getDiagnosticsOptions().isAnalyzeOnStart()) {
      return;
    }

    var serverContext = event.getSource();

    var documentContexts = serverContext.getDocuments().values();
    var progress = workDoneProgressHelper.createProgress(documentContexts.size(), " files");
    progress.beginProgress("Analyzing project");

    documentContexts.forEach((DocumentContext documentContext) -> {

      progress.tick();

      var withContent = documentContext.isWithContent();
      if (!withContent) {
        documentContext.rebuild();
      }
      diagnosticProvider.computeAndPublishDiagnostics(documentContext);
      if (!withContent) {
        documentContext.clearSecondaryData();
      }

    });

    progress.endProgress("Project analyzed");
  }
}
