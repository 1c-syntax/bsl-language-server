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

class LatinAndCyrillicSymbolInWordDiagnosticTest extends AbstractDiagnosticTest<LatinAndCyrillicSymbolInWordDiagnostic> {
  LatinAndCyrillicSymbolInWordDiagnosticTest() {
    super(LatinAndCyrillicSymbolInWordDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(15);
    assertThat(diagnostics, true)
      // методы
      .hasRange(30, 17, 26)
      .hasRange(30, 33, 39)
      .hasRange(34, 10, 19)
      // переменные
      .hasRange(0, 6, 10)
      .hasRange(5, 20, 23)
      .hasRange(12, 10, 12)
      .hasRange(13, 10, 15)
      .hasRange(16, 4, 23)
      .hasRange(35, 10, 21)
      .hasRange(36, 10, 22)
      .hasRange(37, 10, 18)
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
    configuration.put("excludeWords", "Namе, ВИмениEnglish, ComОбъект2, ПеременнаяA");
    configuration.put("allowTrailingPartsInAnotherLanguage", true);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(13);
    assertThat(diagnostics, true)
      // методы
      .hasRange(30, 17, 26)
      .hasRange(30, 33, 39)
      .hasRange(34, 10, 19)
      // переменные
      .hasRange(5, 20, 23)
      .hasRange(12, 10, 12)
      .hasRange(13, 10, 15)
      .hasRange(16, 4, 23)
      .hasRange(36, 10, 22)
      .hasRange(37, 10, 18)
      // аннотации
      .hasRange(19, 1, 10)
      .hasRange(23, 11, 19)
      // остальное
      .hasRange(27, 9, 15)
      .hasRange(31, 13, 19)
    ;

  }

  @Test
  void testConfigureNotAllowTrailing() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("excludeWords", "ЧтениеXML, ЧтениеJSON, ЗаписьXML, ЗаписьJSON, ComОбъект, " +
      "ФабрикаXDTO, ОбъектXDTO, СоединениеFTP, HTTPСоединение, HTTPЗапрос, HTTPСервисОтвет, SMSСообщение, WSПрокси");
    configuration.put("allowTrailingPartsInAnotherLanguage", false);

    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(18);
    assertThat(diagnostics, true)
      // методы
      .hasRange(2, 10, 23)
      .hasRange(9, 8, 21)
      .hasRange(30, 17, 26)
      .hasRange(30, 33, 39)
      .hasRange(34, 10, 19)
      // переменные
      .hasRange(5, 20, 23)
      .hasRange(12, 10, 12)
      .hasRange(13, 10, 15)
      .hasRange(15, 4, 14)
      .hasRange(16, 4, 23)
      .hasRange(36, 10, 22)
      .hasRange(37, 10, 18)
      // аннотации
      .hasRange(19, 1, 10)
      .hasRange(23, 11, 19)
      // остальное
      .hasRange(27, 9, 15)
      .hasRange(31, 13, 19)
    ;

  }

}
