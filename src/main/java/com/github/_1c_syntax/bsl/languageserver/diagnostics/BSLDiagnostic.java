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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import org.eclipse.lsp4j.Diagnostic;

import java.util.List;
import java.util.Map;

/**
 * BSLDiagnostic main purpose is to provide collection of LSP {@link Diagnostic},
 * fired on concrete {@link DocumentContext}.
 * <p>
 * Each BSLDiagnostic implementation MUST contain constructor with exactly one parameter {@link DiagnosticInfo}.
 * Passed DiagnosticInfo MUST be stored as a object field and returned by {@link #getInfo()}.
 * <p>
 * {@link #getDiagnostics(DocumentContext)} method SHOULD use {@link DiagnosticStorage} to add and return diagnostics.
 */
public interface BSLDiagnostic {

  List<Diagnostic> getDiagnostics(DocumentContext documentContext);

  void setInfo(DiagnosticInfo info);

  DiagnosticInfo getInfo();

  /**
   * Настроить параметры диагностики.
   *
   * @param configuration Карта параметров конфигурации
   */
  default void configure(Map<String, Object> configuration) {
    DiagnosticHelper.configureDiagnostic(this, configuration);
  }
}
