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

class LogicalOrInJoinQuerySectionDiagnosticTest extends AbstractDiagnosticTest<LogicalOrInJoinQuerySectionDiagnostic> {
  LogicalOrInJoinQuerySectionDiagnosticTest() {
    super(LogicalOrInJoinQuerySectionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(8);

    assertThat(diagnostics, true)
      .hasRange(12, 62, 12, 65)
      .hasRange(12, 108, 12, 111)
      .hasRange(24, 14, 24, 17)
      .hasRange(26, 14, 26, 17)
      .hasRange(27, 14, 27, 17)
      .hasRange(29, 14, 29, 17)
      .hasRange(30, 14, 30, 17)
      .hasRange(19, 15, 19, 18);

  }
}
