/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

class CodeAfterAsyncCallDiagnosticTest extends AbstractDiagnosticTest<CodeAfterAsyncCallDiagnostic> {
  CodeAfterAsyncCallDiagnosticTest() {
    super(CodeAfterAsyncCallDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(4, 4, 4, 96)
      .hasRange(21, 8, 21, 100)
      .hasRange(34, 8, 34, 100)
      .hasRange(48, 12, 48, 104)
      .hasRange(63, 12, 63, 104)
      .hasRange(78, 12, 78, 104)
      .hasRange(93, 12, 93, 104)
      .hasRange(108, 12, 108, 104)
      .hasRange(123, 12, 123, 104)
      .hasSize(9);

  }
}
