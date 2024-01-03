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

class UsingHardcodePathDiagnosticTest extends AbstractDiagnosticTest<UsingHardcodePathDiagnostic> {
  UsingHardcodePathDiagnosticTest() {
    super(UsingHardcodePathDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    // when
    assertThat(diagnostics).hasSize(19);

    // then
    assertThat(diagnostics, true)
      .hasRange(5, 16, 5, 38)
      .hasRange(6, 16, 6, 50)
      .hasRange(7, 16, 7, 43)
      .hasRange(8, 16, 8, 59)
      .hasRange(9, 16, 9, 38)
      .hasRange(10, 16, 10, 50)
      .hasRange(11, 16, 11, 27)
      .hasRange(12, 16, 12, 27)

      .hasRange(13, 16, 13, 28)
      .hasRange(15, 16, 15, 41)
      .hasRange(16, 16, 16, 44)
      .hasRange(18, 16, 18, 27)
      .hasRange(19, 16, 19, 36)
      .hasRange(20, 16, 20, 37)
      .hasRange(21, 16, 21, 38)

      .hasRange(32, 10, 32, 27)
      .hasRange(33, 23, 33, 60)
      .hasRange(37, 15, 37, 30)
      .hasRange(39, 22, 39, 48)
    ;

  }

  @Test
  void testConfigure() {

    List<Diagnostic> diagnostics;
    Map<String, Object> configuration;
    // Изменяем состав ключевых слов поиска стандартных корневых каталогов Unix
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("searchWordsStdPathsUnix", "home|lib");
    diagnosticInstance.configure(configuration);

    // then
    diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(16);

  }
}
