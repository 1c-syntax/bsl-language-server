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

import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MagicNumberDiagnosticTest extends AbstractDiagnosticTest<MagicNumberDiagnostic> {

  @Test
  void runTest() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(10);
    assertThat(diagnostics, true)
      .hasRange(3, 18, 20)
      .hasRange(3, 23, 25)
      .hasRange(7, 31, 33)
      .hasRange(11, 20, 21)
      .hasRange(20, 21, 23)
      .hasRange(23, 24, 26)
      .hasRange(27, 34, 35)
      .hasRange(33, 37, 38)
      .hasRange(34, 37, 38)
      .hasRange(44, 12, 14);
  }

  @Test
  void testConfigure() {
    Map<String, Object> config = diagnosticInstance.getInfo().getDefaultConfiguration();
    config.put("authorizedNumbers", "-1,0,1,60,7");
    diagnosticInstance.configure(config);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics, true)
      .hasRange(7, 31, 33)
      .hasRange(11, 20, 21)
      .hasRange(20, 21, 23)
      .hasRange(23, 24, 26)
      .hasRange(33, 37, 38)
      .hasRange(34, 37, 38)
      .hasRange(44, 12, 14);
  }

  @Test
  void testIndexes() {
    Map<String, Object> config = diagnosticInstance.getInfo().getDefaultConfiguration();
    config.put("allowMagicIndexes", false);
    diagnosticInstance.configure(config);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(12);
    assertThat(diagnostics, true)
      .hasRange(3, 18, 20)
      .hasRange(3, 23, 25)
      .hasRange(7, 31, 33)
      .hasRange(11, 20, 21)
      .hasRange(20, 21, 23)
      .hasRange(23, 24, 26)
      .hasRange(27, 34, 35)
      .hasRange(33, 37, 38)
      .hasRange(34, 37, 38)
      .hasRange(44, 12, 14)
      .hasRange(49, 32, 34)
      .hasRange(50, 18, 20);
  }

}
