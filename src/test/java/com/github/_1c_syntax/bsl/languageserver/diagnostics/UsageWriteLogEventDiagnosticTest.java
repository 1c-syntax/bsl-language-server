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

class UsageWriteLogEventDiagnosticTest extends AbstractDiagnosticTest<UsageWriteLogEventDiagnostic> {
  UsageWriteLogEventDiagnosticTest() {
    super(UsageWriteLogEventDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(3, 4, 39)
      .hasRange(4, 4, 73)
      .hasRange(5, 4, 77)
      .hasRange(7, 4, 9, 61)
      .hasRange(11, 4, 79)
      .hasRange(16, 6, 17, 25)
      .hasRange(23, 6, 24, 24)
      .hasRange(31, 6, 32, 45)
      .hasRange(38, 6, 39, 37)
      .hasRange(45, 6, 46, 21)
      .hasRange(45, 6, 46, 21)
      .hasRange(190, 6, 192,56)
      .hasRange(204, 6, 206,22)
      .hasRange(219, 6, 221,22)
      .hasRange(286, 12, 291,39)

      .hasRange(354, 6, 356,73)
      .hasRange(368, 6, 370,22)
      .hasRange(383, 6, 385,22)
      .hasRange(439, 12, 444,39)
      .hasSize(18)
    ;

  }
}
