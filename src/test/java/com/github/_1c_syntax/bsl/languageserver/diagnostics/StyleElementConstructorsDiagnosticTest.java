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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class StyleElementConstructorsDiagnosticTest extends AbstractDiagnosticTest<StyleElementConstructorsDiagnostic> {
  StyleElementConstructorsDiagnosticTest() {
    super(StyleElementConstructorsDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(15);
    assertThat(diagnostics, true)
      .hasRange(2, 12, 37)
      .hasRange(3, 12, 33)
      .hasRange(4, 12, 25)
      .hasRange(8, 9, 33)
      .hasRange(9, 9, 31)
      .hasRange(10, 9, 19)
      .hasRange(12, 9, 23)
      .hasRange(13, 9, 33)
      .hasRange(14, 9, 37)
      .hasRange(24, 39, 53)
      .hasRange(25, 39, 63)
      .hasRange(26, 39, 67)
      .hasRange(28, 39, 52)
      .hasRange(29, 39, 60)
      .hasRange(30, 39, 64);

  }
}
