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

class UsingHardcodeNetworkAddressDiagnosticTest extends AbstractDiagnosticTest<UsingHardcodeNetworkAddressDiagnostic> {
  UsingHardcodeNetworkAddressDiagnosticTest() {
    super(UsingHardcodeNetworkAddressDiagnostic.class);
  }

  @Test
  void test() {
    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(12);

    assertThat(diagnostics, true)
      .hasRange(2, 15, 2, 31)
      .hasRange(6, 23, 6, 39)
      .hasRange(7, 23, 7, 34)
      .hasRange(9, 23, 9, 64)
      .hasRange(10, 23, 10, 64)
      .hasRange(12, 44, 12, 85)
      .hasRange(20, 18, 20, 29)
      .hasRange(23, 7, 23, 119)
      .hasRange(55, 13, 18)
      .hasRange(57, 104, 114)
      .hasRange(65, 9, 22)
      .hasRange(71, 6, 15);
  }

  @Test
  void testConfigure() {
    List<Diagnostic> diagnostics;
    Map<String, Object> configuration;

    // Проверяем количество срабатываний без изменения параметров
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(12);

    // Изменяем ключевые слова исключения для поиска IP адресов
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("searchWordsExclusion", "Version");
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(13);

    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    // убираем 2.* из исключения
    configuration.put("searchPopularVersionExclusion", "^(1|3|8\\.3|11)\\.");
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(15);
  }
}
