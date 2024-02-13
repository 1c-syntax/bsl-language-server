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
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class UsingServiceTagDiagnosticTest extends AbstractDiagnosticTest<UsingServiceTagDiagnostic> {

  UsingServiceTagDiagnosticTest() {
    super(UsingServiceTagDiagnostic.class);
  }

  @Test
  void runTest() {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(21);
    assertThat(diagnostics, true)
      .hasRange(1, 0, 1, 36)
      .hasRange(13, 4, 13, 50)
      .hasRange(21, 4, 21, 29)
      .hasRange(25, 0, 25, 26)
      .hasRange(26, 33, 26, 58)
      .hasRange(28, 4, 28, 11)
      .hasRange(29, 20, 29, 30)
      .hasRange(31, 4, 31, 12)
      .hasRange(32, 21, 32, 31)
      .hasRange(34, 8, 34, 21)
      .hasRange(42, 4, 42, 51)
      .hasRange(61, 4, 61, 51)
      .hasRange(65, 0, 65, 11)
      .hasRange(67, 0, 67, 11)
      .hasRange(71, 4, 71, 36)
      .hasRange(77, 4, 77, 39)
      .hasRange(82, 4, 82, 27)
      .hasRange(88, 4, 88, 31)
      .hasRange(98, 4, 98, 38)
      .hasRange(105, 4, 105, 28)
      .hasRange(112, 4, 112, 29);

  }

  @Test
  void runTestWithConfigure() {
    // conf
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("serviceTags", "todo");
    diagnosticInstance.configure(configuration);

    //when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(1, 0, 1, 36)
      .hasRange(21, 4, 21, 29);
  }
}
