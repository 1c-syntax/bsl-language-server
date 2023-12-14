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
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MagicDateDiagnosticTest extends AbstractDiagnosticTest<MagicDateDiagnostic> {
  MagicDateDiagnosticTest() {
    super(MagicDateDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(13);
    assertThat(diagnostics, true)
      .hasRange(11, 12, 22)
      .hasRange(12, 12, 28)
      .hasRange(13, 7, 17)
      .hasRange(14, 14, 24)
      .hasRange(23, 7, 26)
      .hasRange(25, 87, 97)
      .hasRange(26, 80, 90)
      .hasRange(26, 92, 102)
      .hasRange(27, 22, 32)
      .hasRange(28, 19, 35)
      .hasRange(29, 10, 26)
      .hasRange(29, 29, 39)
      .hasRange(31, 64, 80);

  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("authorizedDates", "00010101,00010101000000,000101010000,00050101,00020501121314,12340101, 00020101   ,,");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(12, 12, 28)
      .hasRange(13, 7, 17)
      .hasRange(23, 7, 26)
      .hasRange(27, 22, 32)
      .hasRange(29, 29, 39)
      .hasRange(31, 64, 80);
  }
}
