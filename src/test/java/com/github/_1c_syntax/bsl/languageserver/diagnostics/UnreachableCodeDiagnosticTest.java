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


class UnreachableCodeDiagnosticTest extends AbstractDiagnosticTest<UnreachableCodeDiagnostic> {
  UnreachableCodeDiagnosticTest() {
    super(UnreachableCodeDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(14);
    assertThat(diagnostics, true)
      .hasRange(12, 12, 12, 20)
      .hasRange(21, 12, 21, 20)
      .hasRange(30, 12, 30, 20)
      .hasRange(37, 4, 41, 15)
      .hasRange(46, 4, 51, 15)
      .hasRange(58, 12, 58, 20)
      .hasRange(67, 12, 69, 21)
      .hasRange(82, 16, 84, 25)
      .hasRange(93, 8, 93, 16)
      .hasRange(102, 8, 102, 17)
      .hasRange(108, 16, 112, 26)
      .hasRange(134, 4, 134, 13)
      .hasRange(139, 4, 141, 13)
      .hasRange(145, 0, 145, 9);
  }
}
