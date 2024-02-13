/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

class NestedFunctionInParametersDiagnosticTest extends AbstractDiagnosticTest<NestedFunctionInParametersDiagnostic> {
  NestedFunctionInParametersDiagnosticTest() {
    super(NestedFunctionInParametersDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(1, 22, 30)
      .hasRange(3, 11, 19)
      .hasRange(51, 72, 94)
    ;
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("allowOneliner", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(12);
    assertThat(diagnostics, true)
      .hasRange(1, 22, 30)
      .hasRange(3, 11, 19)
      .hasRange(3, 20, 49)
      .hasRange(8, 4, 12)
      .hasRange(13, 35, 42)
      .hasRange(17, 22, 31)
      .hasRange(36, 14, 19)
      .hasRange(47, 72, 94)
      .hasRange(51, 72, 94)
      .hasRange(56, 4, 28)
      .hasRange(69, 16, 21)
      .hasRange(79, 24, 43)
    ;

  }

  @Test
  void testConfigureMethods() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("allowedMethodNames", "НСтр, ПредопределенноеЗначение, PredefinedValue, ДругаяФункция");
    configuration.put("allowOneliner", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(13);
    assertThat(diagnostics, true)
      .hasRange(1, 22, 30)
      .hasRange(3, 11, 19)
      .hasRange(3, 20, 49)
      .hasRange(8, 4, 12)
      .hasRange(13, 35, 42)
      .hasRange(17, 22, 31)
      .hasRange(36, 14, 19)
      .hasRange(47, 72, 94)
      .hasRange(51, 72, 94)
      .hasRange(56, 4, 28)
      .hasRange(72, 15, 20)
      .hasRange(79, 24, 43)
      .hasRange(82, 11, 26)
    ;

  }
}
