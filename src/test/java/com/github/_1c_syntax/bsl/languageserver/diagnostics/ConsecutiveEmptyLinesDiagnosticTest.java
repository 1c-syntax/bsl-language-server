/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

class ConsecutiveEmptyLinesDiagnosticTest extends AbstractDiagnosticTest<ConsecutiveEmptyLinesDiagnostic> {
  ConsecutiveEmptyLinesDiagnosticTest() {
    super(ConsecutiveEmptyLinesDiagnostic.class);
  }

  @Test
  void test_CRLF_And_Spaces() {
    checkModuleText(false);
  }

  @Test
  void test_CR_And_Spaces_And_Tab() {
    checkModuleText(true);
  }

  void checkModuleText(boolean use_CR_WithTab) {

    String module = getText();

    if (use_CR_WithTab){
      module = module.replace("\n", "\r");
      module = module.replace("  ", "\t");
    }

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(1, 0, 2, 0)
      .hasRange(6, 0, 7, 0)
      .hasRange(11, 0, 12, 0)
      .hasRange(15, 0, 16, 0)
      .hasRange(18, 0, 19, 0)
      .hasRange(23, 0, 24, 0)
      .hasRange(27, 0, 28, 0)
      .hasRange(30, 0, 31, 0)
      .hasRange(34, 0, 35, 0)
      .hasSize(9)
    ;

  }

  @Test
  void test_ConfigureEmptyLineParam() {
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("allowedEmptyLinesCount", 3);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(30, 0, 31, 0)
      .hasSize(1)
    ;
  }
}
