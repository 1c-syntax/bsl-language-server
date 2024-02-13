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

class IncorrectLineBreakDiagnosticTest extends AbstractDiagnosticTest<IncorrectLineBreakDiagnostic> {
  IncorrectLineBreakDiagnosticTest() {
    super(IncorrectLineBreakDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(6, 32, 33)
      .hasRange(7, 35, 36)
      .hasRange(15, 32, 33)
      .hasRange(16, 22, 23)
      .hasRange(20, 49, 50)
      .hasRange(69, 80, 83)
      .hasRange(82, 89, 92)
      .hasRange(44, 25, 76)
      .hasRange(46, 25, 79)
      .hasRange(58, 4, 55)
      .hasRange(60, 4, 58)
      .hasRange(101, 2, 3)
      .hasRange(105, 2, 3)
      .hasRange(109, 2, 3)
      .hasSize(14)
    ;

  }

  @Test
  void testDisableDiagnostics() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("checkFirstSymbol", false);
    configuration.put("listOfIncorrectFirstSymbol", "\\)|;|,|\\);");
    configuration.put("checkLastSymbol", false);
    configuration.put("listOfIncorrectLastSymbol", "ИЛИ|И|OR|AND|\\+|-|/|%|\\*");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testTrailingBraceCodestyle() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("checkFirstSymbol", true);
    configuration.put("listOfIncorrectFirstSymbol", ";|,|\\);");
    configuration.put("checkLastSymbol", true);
    configuration.put("listOfIncorrectLastSymbol", "ИЛИ|И|OR|AND|\\+|-|/|%|\\*");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(12);
    assertThat(diagnostics, true)
      .hasRange(6, 32, 33)
      .hasRange(7, 35, 36)
      .hasRange(15, 32, 33)
      .hasRange(16, 22, 23)
      .hasRange(20, 49, 50)
      .hasRange(69, 80, 83)
      .hasRange(82, 89, 92)
      .hasRange(44, 25, 26)
      .hasRange(46, 25, 26)
      .hasRange(58, 4, 5)
      .hasRange(60, 4, 5)
      .hasRange(134, 4, 5)
    ;

  }
}
