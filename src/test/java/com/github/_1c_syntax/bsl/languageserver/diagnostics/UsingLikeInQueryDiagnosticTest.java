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

class UsingLikeInQueryDiagnosticTest extends AbstractDiagnosticTest<UsingLikeInQueryDiagnostic> {
  UsingLikeInQueryDiagnosticTest() {
    super(UsingLikeInQueryDiagnostic.class);
  }

  @Test
  void test() {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(21);
    assertThat(diagnostics, true)
      .hasRange(4, 8, 39)
      .hasRange(5, 8, 39)
      .hasRange(6, 8, 44)
      .hasRange(7, 9, 48)
      .hasRange(8, 8, 39)
      .hasRange(9, 8, 36)
      .hasRange(10, 8, 35)
      .hasRange(11, 8, 53)
      .hasRange(17, 15, 46)
      .hasRange(18, 16, 47)
      .hasRange(19, 16, 52)
      .hasRange(20, 17, 48)
      .hasRange(21, 16, 47)
      .hasRange(24, 15, 51)
      .hasRange(25, 18, 49)
      .hasRange(26, 18, 49)
      .hasRange(27, 18, 49)
      .hasRange(29, 8, 44)
      .hasRange(30, 10, 41)
      .hasRange(31, 10, 41)
      .hasRange(32, 10, 41)
    ;
  }
}
