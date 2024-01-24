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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;


class IdenticalExpressionsDiagnosticTest extends AbstractDiagnosticTest<IdenticalExpressionsDiagnostic> {

  IdenticalExpressionsDiagnosticTest() {
    super(IdenticalExpressionsDiagnostic.class);
  }

  @Test
  void runTest() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(20);
    assertThat(diagnostics, true)
      .hasRange(4, 9, 4, 25)
      .hasRange(6, 16, 6, 31)
      .hasRange(11, 13, 11, 28)
      .hasRange(13, 9, 13, 66)
      .hasRange(15, 16, 15, 34)
      .hasRange(19, 9, 19, 85)
      .hasRange(21, 16, 21, 33)
      .hasRange(25, 9, 25, 38)
      .hasRange(27, 16, 27, 43)
      .hasRange(31, 16, 31, 33)
      .hasRange(39, 4, 39, 43)
      .hasRange(40, 5, 40, 20)
      .hasRange(42, 10, 42, 25)
      .hasRange(44, 10, 44, 27)
      .hasRange(46, 10, 46, 52)
      .hasRange(48, 10, 48, 29)
      .hasRange(52, 4, 52, 32)
      .hasRange(53, 4, 53, 43)
      .hasRange(64, 12, 66, 10)
      .hasRange(70, 12, 76, 10)
    ;
  }

  @Test
  void checkMessage() {
    var code = """
      А = ТипДокумента = Тип("ДокументСсылка.ПриходнаяНакладная")
      Или ТипДокумента = Тип("ДокументСсылка.СчетНаОплатуПоставщика")
      Или ТипДокумента = Тип("ДокументСсылка.КорректировкаПоступления")
      Или ТипДокумента = Тип("ДокументСсылка.ЗаказПоставщику")
      Или ТипДокумента = Тип("ДокументСсылка.СчетНаОплатуПоставщика")""";

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.get(0).getMessage()).contains("\"ДокументСсылка.СчетНаОплатуПоставщика\"");
  }

  @Test
  void testThatPopularQuantificationSkipped() {
    var code = """
      А = Байты / 1024 / 1024;
      В = Время / 24 / 60 / 60;
      Б = Байты = 1024 / "1024\"""";

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).isEmpty();

  }

  @Test
  void testThatConfiguredPopularQuantificationSkipped() {
    var code = """
      А = Байты / 1024 / 1024;
      В = Время / 24 / 60 / 60;
      Б = Байты = 1024 / "1024\"""";

    // получение текущей конфигурации диагностики
    var configuration = diagnosticInstance.getInfo().getDefaultConfiguration();

    // установка нового значения
    configuration.put("popularDivisors", "1024");

    // переконфигурирование
    diagnosticInstance.configure(configuration);

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.get(0).getMessage()).contains("60");

  }

}

