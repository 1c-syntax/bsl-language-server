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

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class DoubleNegativesDiagnosticTest extends AbstractDiagnosticTest<DoubleNegativesDiagnostic> {
  DoubleNegativesDiagnosticTest() {
    super(DoubleNegativesDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(1, 5, 1, 73)
      .hasRange(7, 4, 7, 19)
      .hasRange(8, 4, 8, 20)
      .hasRange(9, 4, 9, 20)
      .hasRange(10, 4, 10, 21)
      .hasRange(11, 4, 11, 42)
      .hasRange(12, 4, 12, 42)
      .hasRange(13, 4, 13, 25)
      .hasRange(14, 4, 14, 24)
      .hasRange(19, 5, 19, 38)
      .hasRange(23, 19, 23, 39)
      .hasRange(32, 4, 32, 26)
      .hasRange(33, 4, 33, 36)
      .hasRange(39, 4, 39, 19)
    ;

  }
}
