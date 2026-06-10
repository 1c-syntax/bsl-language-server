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

class BadExceptionCategoryDiagnosticTest extends AbstractDiagnosticTest<BadExceptionCategoryDiagnostic> {
  BadExceptionCategoryDiagnosticTest() {
    super(BadExceptionCategoryDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics, true)
      .hasRange(8, 4, 8, 57)
      .hasRange(9, 4, 9, 60)
      .hasRange(10, 4, 10, 82)
      .hasRange(11, 4, 11, 87)
      .hasRange(12, 4, 12, 85)
      .hasRange(15, 4, 15, 42)
      .hasRange(16, 4, 16, 55);

  }
}
