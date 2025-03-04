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

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MultilingualStringHasAllDeclaredLanguagesDiagnosticTest
  extends AbstractDiagnosticTest<MultilingualStringHasAllDeclaredLanguagesDiagnostic> {

  MultilingualStringHasAllDeclaredLanguagesDiagnosticTest() {
    super(MultilingualStringHasAllDeclaredLanguagesDiagnostic.class);
  }

  @Test
  void testOnlyRU() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(3);

    assertThat(diagnostics, true)
      .hasRange(12, 16, 12, 22)
      .hasRange(13, 30, 13, 86)
      .hasRange(16, 30, 16, 66);
  }

  @Test
  void testRuAndEn() {
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("declaredLanguages", "ru,en");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(8);

    assertThat(diagnostics, true)
      .hasRange(12, 16, 12, 22)
      .hasRange(13, 30, 13, 86)
      .hasRange(15, 27, 15, 65)
      .hasRange(15, 27, 15, 65)
      .hasRange(27, 37, 27, 75)
      .hasRange(31, 67, 31, 86)
      .hasRange(33, 69, 33, 97)
      .hasRange(42, 8, 42, 89);
  }
}
