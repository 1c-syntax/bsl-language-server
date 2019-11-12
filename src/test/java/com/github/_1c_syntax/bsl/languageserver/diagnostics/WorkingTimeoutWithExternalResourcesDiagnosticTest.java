/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

class WorkingTimeoutWithExternalResourcesDiagnosticTest extends AbstractDiagnosticTest<WorkingTimeoutWithExternalResourcesDiagnostic> {
  WorkingTimeoutWithExternalResourcesDiagnosticTest() {
    super(WorkingTimeoutWithExternalResourcesDiagnostic.class);
  }

  @Test
  void test() {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(8);

    // check ranges
    assertThat(diagnostics, true)
      .hasRange(3, 20, 3, 75)
      .hasRange(5, 20, 5, 92)
      .hasRange(7, 18, 7, 72)
      .hasRange(11, 16, 11, 80)
      .hasRange(19, 21, 19, 65)
      .hasRange(32, 14, 32, 43)
      .hasRange(69, 26, 69, 114)
      .hasRange(76, 10, 76, 39)
    ;

  }
}
