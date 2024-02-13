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

class DeprecatedAttributes8312DiagnosticTest extends AbstractDiagnosticTest<DeprecatedAttributes8312Diagnostic> {
  DeprecatedAttributes8312DiagnosticTest() {
    super(DeprecatedAttributes8312Diagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(45);
    assertThat(diagnostics, true)
      .hasRange(1, 37, 1, 53)
      .hasRange(2, 30, 2, 41)
      .hasRange(3, 30, 3, 40)
      .hasRange(4, 30, 4, 58)
      .hasRange(5, 30, 5, 58)
      .hasRange(6, 30, 6, 61)
      .hasRange(7, 30, 7, 59)
      .hasRange(8, 30, 8, 50)
      .hasRange(9, 30, 9, 46)
      .hasRange(13, 17, 13, 27)
      .hasRange(14, 17, 14, 39)
      .hasRange(15, 17, 15, 39)
      .hasRange(16, 17, 16, 39)
      .hasRange(17, 17, 17, 37)
      .hasRange(18, 17, 18, 34)
      .hasRange(19, 17, 19, 35)
      .hasRange(23, 13, 23, 31)
      .hasRange(24, 13, 24, 33)
      .hasRange(25, 18, 25, 36)
      .hasRange(26, 18, 26, 38)
      .hasRange(27, 20, 27, 38)
      .hasRange(28, 20, 28, 40)
      .hasRange(30, 13, 30, 27)
      .hasRange(31, 13, 31, 42)
      .hasRange(32, 13, 32, 41)
      .hasRange(33, 13, 33, 60)
      .hasRange(35, 21, 35, 38)
      .hasRange(36, 14, 36, 45)
      .hasRange(40, 9, 40, 20)
      .hasRange(41, 14, 41, 25)
      .hasRange(42, 14, 42, 25)
      .hasRange(43, 9, 43, 19)
      .hasRange(44, 14, 44, 24)
      .hasRange(45, 14, 45, 24)
      .hasRange(47, 9, 47, 22)
      .hasRange(48, 9, 48, 35)
      .hasRange(49, 9, 49, 33)
      .hasRange(50, 9, 50, 34)
      .hasRange(52, 10, 52, 22)
      .hasRange(53, 10, 53, 26)
      .hasRange(58, 17, 58, 46)
      .hasRange(62, 4, 62, 36)
      .hasRange(66, 4, 66, 25)
      .hasRange(70, 54, 70, 69)
      .hasRange(74, 30, 74, 41);

  }
}
