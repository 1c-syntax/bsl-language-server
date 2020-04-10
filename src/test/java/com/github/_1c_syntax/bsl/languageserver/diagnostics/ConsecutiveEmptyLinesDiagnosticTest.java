/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

class ConsecutiveEmptyLinesDiagnosticTest extends AbstractDiagnosticTest<ConsecutiveEmptyLinesDiagnostic> {
  ConsecutiveEmptyLinesDiagnosticTest() {
    super(ConsecutiveEmptyLinesDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(1, 0, 2, 0)
      .hasRange(5, 0, 6, 0)
      .hasRange(9, 0, 10, 0)
      .hasRange(13, 0, 14, 0)
      .hasRange(17, 0, 18, 0)
      .hasRange(22, 0, 23, 0)
      .hasRange(26, 0, 27, 0)
      .hasRange(29, 0, 30, 0)
      .hasRange(33, 0, 34, 0)
    ;

  }
}
