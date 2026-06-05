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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticInfos;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.Map;

/**
 * Базовый класс для reporters, которым нужен доступ к per-workspace данным.
 */
@RequiredArgsConstructor
public abstract class AbstractDiagnosticReporter implements DiagnosticReporter {

  /** Провайдер per-workspace {@link ServerContext}. */
  protected final ServerContextProvider serverContextProvider;
  /** Реестр метаинформации о диагностиках. */
  protected final DiagnosticInfos diagnosticInfos;

  /**
   * Получить {@link ServerContext} для workspace из {@link AnalysisInfo}.
   *
   * @param analysisInfo информация об анализе, содержащая каталог исходников workspace
   * @return контекст workspace, соответствующий каталогу исходников
   */
  protected ServerContext getServerContext(AnalysisInfo analysisInfo) {
    var workspaceUri = Path.of(analysisInfo.sourceDir()).toUri();
    return serverContextProvider.getServerContext(workspaceUri)
      .orElseThrow(() -> new IllegalStateException("No workspace found for " + workspaceUri));
  }

  /**
   * Получить соответствие «код диагностики → {@link DiagnosticInfo}».
   *
   * @return карта метаинформации о диагностиках по их кодам
   */
  protected Map<String, DiagnosticInfo> getDiagnosticInfosByCode() {
    return diagnosticInfos.getByCode();
  }
}
