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

class SelectTopWithoutOrderByDiagnosticTest extends AbstractDiagnosticTest<SelectTopWithoutOrderByDiagnostic> {
  SelectTopWithoutOrderByDiagnosticTest() {
    super(SelectTopWithoutOrderByDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(10);
    assertThat(diagnostics, true)
      .hasRange(36, 13, 22)
      .hasRange(54, 13, 22)
      .hasRange(76, 20, 29)
      .hasRange(101, 15, 24)
      .hasRange(132, 20, 29)
      .hasRange(155, 13, 22)
      .hasRange(162, 13, 21)
      .hasRange(148, 20, 29)
      .hasRange(188, 16, 25)
      .hasRange(181, 24, 33)
    ;

  }

  @Test
  void testConfigure() {
    // given
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("skipSelectTopOne", false);
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(14);
    assertThat(diagnostics, true)
      .hasRange(2, 13, 21)
      .hasRange(20, 13, 21)
      .hasRange(36, 13, 22)
      .hasRange(54, 13, 22)
      .hasRange(76, 20, 29)
      .hasRange(90, 20, 28)
      .hasRange(101, 15, 24)
      .hasRange(115, 15, 23)
      .hasRange(132, 20, 29)
      .hasRange(155, 13, 22)
      .hasRange(162, 13, 21)
      .hasRange(148, 20, 29)
      .hasRange(188, 16, 25)
      .hasRange(181, 24, 33)
    ;

  }
}
