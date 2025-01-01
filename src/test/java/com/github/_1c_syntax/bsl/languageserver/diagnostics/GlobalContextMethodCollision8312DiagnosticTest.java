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

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class GlobalContextMethodCollision8312DiagnosticTest extends AbstractDiagnosticTest<GlobalContextMethodCollision8312Diagnostic> {
  GlobalContextMethodCollision8312DiagnosticTest() {
    super(GlobalContextMethodCollision8312Diagnostic.class);
  }

  @Test
  void test8312() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(20);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 20)
      .hasRange(3, 8, 31)
      .hasRange(6, 8, 21)
      .hasRange(9, 8, 18)
      .hasRange(12, 8, 20)
      .hasRange(15, 8, 19)
      .hasRange(18, 8, 20)
      .hasRange(21, 8, 34)
      .hasRange(24, 8, 27)
      .hasRange(27, 8, 28)
      .hasRange(30, 8, 16)
      .hasRange(33, 8, 22)
      .hasRange(36, 8, 14)
      .hasRange(39, 8, 18)
      .hasRange(42, 8, 17)
      .hasRange(45, 8, 18)
      .hasRange(48, 8, 21)
      .hasRange(51, 8, 18)
      .hasRange(54, 8, 24)
      .hasRange(57, 8, 25)
    ;
  }
}
