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

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class FunctionReturnsSamePrimitiveDiagnosticTest extends AbstractDiagnosticTest<FunctionReturnsSamePrimitiveDiagnostic> {
  FunctionReturnsSamePrimitiveDiagnosticTest() {
    super(FunctionReturnsSamePrimitiveDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics;
    Map<String, Object> configuration;

    // Проверяем срабатывания с параметрами по умолчанию
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics, true)
      .hasSize(5)
      .hasRange(0, 8, 23)
      .hasRange(25, 8, 14)
      .hasRange(35, 8, 17)
      .hasRange(62, 8, 22)
      .hasRange(82, 8, 32);

    // Проверяем с выключенным параметром skipAttachable
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("skipAttachable", false);
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics, true)
      .hasSize(7)
      .hasRange(0, 8, 23)
      .hasRange(25, 8, 14)
      .hasRange(35, 8, 17)
      .hasRange(52, 8, 35)
      .hasRange(62, 8, 22)
      .hasRange(72, 9, 32)
      .hasRange(82, 8, 32);

    // Проверяем с включенным параметром caseSensitiveForString
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("caseSensitiveForString", true);
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics, true)
      .hasSize(4)
      .hasRange(0, 8, 23)
      .hasRange(25, 8, 14)
      .hasRange(35, 8, 17)
      .hasRange(62, 8, 22);

  }
}
