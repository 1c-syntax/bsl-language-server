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

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class CodeOutOfRegionDiagnosticTest extends AbstractDiagnosticTest<CodeOutOfRegionDiagnostic> {
  CodeOutOfRegionDiagnosticTest() {
    super(CodeOutOfRegionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics, true)
      .hasRange(4, 0, 8)
      // TODO так вообще то правильно, но пока следующая строка .hasRange(9, 0, 9)
      .hasRange(8, 0, 9, 9)
      .hasRange(17, 10, 13)
      .hasRange(24, 10, 12)
      .hasRange(46, 0, 13)
      .hasRange(57, 0, 7)
      .hasRange(59, 0, 69, 9)
    ;

  }

  @Test
  void emptyTest() {

    List<Diagnostic> diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticEmpty");
    assertThat(diagnostics).hasSize(0);

  }

  @Test
  void testNoRegions() {

    List<Diagnostic> diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticNoRegions");

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(4, 0, 22, 0);

  }

  @Test
  void testCodeBlock() {

    List<Diagnostic> diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticCodeBlock");

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 0, 0, 23);

  }


  @Test
  void testEmptyFile() {

    List<Diagnostic> diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticEmptyFile");
    assertThat(diagnostics).isEmpty();

  }

}
