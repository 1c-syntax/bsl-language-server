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

class InternetAccessDiagnosticTest extends AbstractDiagnosticTest<InternetAccessDiagnostic> {
  InternetAccessDiagnosticTest() {
    super(InternetAccessDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(1, 20, 75)
      .hasRange(3, 18, 72)
      .hasRange(5, 16, 80)
      .hasRange(8, 8, 111)
      .hasRange(13, 21, 65)
      .hasRange(14, 17, 35)
      .hasRange(15, 17, 47)
      .hasRange(16, 17, 43)
      .hasRange(17, 21, 51)
      .hasRange(21, 21, 65)
      .hasRange(22, 17, 35)
      .hasRange(23, 17, 47)
      .hasRange(24, 17, 43)
      .hasRange(25, 21, 51)
      .hasRange(29, 14, 43)
      .hasRange(35, 14, 32)
      .hasRange(39, 14, 35)
      .hasRange(42, 10, 21)
      .hasSize(18);
  }
}
