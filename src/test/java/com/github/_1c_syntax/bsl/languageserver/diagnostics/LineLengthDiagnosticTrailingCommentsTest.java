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

class LineLengthDiagnosticTrailingCommentsTest extends AbstractDiagnosticTest<LineLengthDiagnostic> {

  LineLengthDiagnosticTrailingCommentsTest() {
    super(LineLengthDiagnostic.class);
  }

  @Override
  protected String getTestFile() {
    return "LineLengthDiagnosticTrailingComments.bsl";
  }

  @Test
  void testDefault() {
    // when - default configuration includes trailing comments
    List<Diagnostic> diagnostics = getDiagnostics();

    // then - should flag lines 3, 5, 7, 9 
    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(2, 0, 2, 130)  // line 3: long line without comment
      .hasRange(4, 0, 4, 138)  // line 5: short line + long trailing comment  
      .hasRange(6, 0, 6, 139)  // line 7: long line + trailing comment
      .hasRange(8, 0, 8, 123); // line 9: long comment-only line
  }

  @Test
  void testExcludeTrailingComments() {
    // given
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("excludeTrailingComments", true);
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then - should only flag lines 3 and 9 (lines 5 and 7 should be excluded since their code parts are short)
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(2, 0, 2, 130)  // line 3: long line without comment
      .hasRange(8, 0, 8, 123); // line 9: long comment-only line (not a trailing comment)
  }
}