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
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class LatinAndCyrillicSymbolInWordDiagnosticTest extends AbstractDiagnosticTest<LatinAndCyrillicSymbolInWordDiagnostic> {
  LatinAndCyrillicSymbolInWordDiagnosticTest() {
    super(LatinAndCyrillicSymbolInWordDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(14);
    assertThat(diagnostics, true)
      // методы
      .hasRange(2, 10, 23)
      .hasRange(9, 8, 21)
      .hasRange(30, 17, 26)
      .hasRange(30, 33, 39)
      // переменные
      .hasRange(0, 6, 10)
      .hasRange(5, 20, 23)
      .hasRange(12, 10, 12)
      .hasRange(13, 10, 15)
      .hasRange(15, 4, 14)
      .hasRange(16, 4, 23)
      // аннотации
      .hasRange(19, 1, 10)
      .hasRange(23, 11, 19)
      // остальное
      .hasRange(27, 9, 15)
      .hasRange(31, 13, 19)
    ;
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("excludeWords", "Namе, ВИмениEnglish, ComОбъект2");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(11);
    assertThat(diagnostics, true)
      // методы
      .hasRange(9, 8, 21)
      .hasRange(30, 17, 26)
      .hasRange(30, 33, 39)
      // переменные
      .hasRange(5, 20, 23)
      .hasRange(12, 10, 12)
      .hasRange(13, 10, 15)
      .hasRange(16, 4, 23)
      // аннотации
      .hasRange(19, 1, 10)
      .hasRange(23, 11, 19)
      // остальное
      .hasRange(27, 9, 15)
      .hasRange(31, 13, 19)
    ;

  }
}
