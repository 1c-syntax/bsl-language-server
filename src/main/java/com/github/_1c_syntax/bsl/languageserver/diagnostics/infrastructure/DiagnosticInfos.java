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
package com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.info.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.utils.StringInterner;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-workspace collection of DiagnosticInfo instances.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class DiagnosticInfos {

  private final DiagnosticInfosFactory diagnosticInfosFactory;
  private final LanguageServerConfiguration configuration;
  private final StringInterner stringInterner;

  @Getter
  private Map<String, DiagnosticInfo> byCode;

  @Getter
  private Map<Class<? extends BSLDiagnostic>, DiagnosticInfo> byClass;

  @PostConstruct
  public void init() {
    var diagnosticClasses = diagnosticInfosFactory.getDiagnosticClasses();
    var infos = diagnosticClasses.stream()
      .map(diagnosticClass -> new DiagnosticInfo(diagnosticClass, configuration, stringInterner))
      .toList();

    byCode = infos.stream()
      .collect(Collectors.toMap(info -> info.getCode().getStringValue(), Function.identity()));
    byClass = infos.stream()
      .collect(Collectors.toMap(DiagnosticInfo::getDiagnosticClass, Function.identity()));
  }

  /**
   * Refresh all DiagnosticInfo instances (re-read configuration values).
   */
  public void refresh() {
    byCode.values().forEach(DiagnosticInfo::refresh);
  }
}
