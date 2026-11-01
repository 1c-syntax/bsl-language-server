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

class JoinWithVirtualTableDiagnosticTest extends AbstractDiagnosticTest<JoinWithVirtualTableDiagnostic> {
  JoinWithVirtualTableDiagnosticTest() {
    super(JoinWithVirtualTableDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(3, 84, 119)
      .hasRange(12, 5, 56)
      .hasRange(22, 5, 56)
      .hasRange(31, 9, 53)
      .hasRange(33, 5, 56);

  }

  @Test
  void testTabularSectionNamedAsVirtualTable() {
    var diagnostics = getDiagnostics("JoinWithVirtualTableTabularSection");

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(21, 35, 76);
  }

  @Test
  void testVirtualTableKinds() {
    var diagnostics = getDiagnostics("JoinWithVirtualTableKinds");

    assertThat(diagnostics).hasSize(11);
    assertThat(diagnostics, true)
      .hasRange(7, 30, 71)
      .hasRange(9, 30, 78)
      .hasRange(11, 30, 86)
      .hasRange(13, 30, 94)
      .hasRange(15, 30, 96)
      .hasRange(17, 30, 90)
      .hasRange(19, 30, 105)
      .hasRange(21, 30, 93)
      .hasRange(23, 30, 96)
      .hasRange(25, 30, 80)
      .hasRange(27, 30, 70);
  }
}
