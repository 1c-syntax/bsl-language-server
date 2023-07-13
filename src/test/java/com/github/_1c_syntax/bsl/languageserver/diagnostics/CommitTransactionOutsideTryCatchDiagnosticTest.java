/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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


class CommitTransactionOutsideTryCatchDiagnosticTest extends AbstractDiagnosticTest<CommitTransactionOutsideTryCatchDiagnostic> {
  CommitTransactionOutsideTryCatchDiagnosticTest() {
    super(CommitTransactionOutsideTryCatchDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(36, 4, 36, 30)
      .hasRange(45, 12, 45, 38)
      .hasRange(57, 8, 57, 34)
      .hasRange(66, 4, 66, 30)
      .hasRange(74, 8, 74, 34)
      .hasRange(86, 8, 86, 34)
      .hasRange(98, 8, 98, 34)
      .hasRange(106, 0, 106, 26);
  }

  @Test
  void testSingleSub() {

    List<Diagnostic> diagnostics = getDiagnostics("CommitTransactionOutsideTryCatchDiagnosticSingleSub");

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(3, 4, 3, 30);
  }
}
