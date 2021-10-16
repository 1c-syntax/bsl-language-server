/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MissingQueryParameterDiagnosticTest extends AbstractDiagnosticTest<MissingQueryParameterDiagnostic> {
  MissingQueryParameterDiagnosticTest() {
    super(MissingQueryParameterDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(7, 34, 43)
      .hasRange(35, 34, 43)
      .hasSize(2)
    ;

  }

  @Test
  void testNewQuery() {
    var sample =
      "  Запрос1 = Новый Запрос(\n" +
        "    \"ВЫБРАТЬ\n" +
        "    |  Справочник1.Ссылка КАК Ссылка\n" +
        "    |ИЗ\n" +
        "    |  Справочник.Справочник1 КАК Справочник1\n" +
        "    |ГДЕ\n" +
        "    |  Справочник1.Ссылка = &Параметр\");\n" +
        "\n" +
        "  РезультатЗапроса = Запрос1.Выполнить();\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void testNewQueryNoError() {
    var sample =
      "  Запрос1 = Новый Запрос(\n" +
        "    \"ВЫБРАТЬ\n" +
        "    |  Справочник1.Ссылка КАК Ссылка\n" +
        "    |ИЗ\n" +
        "    |  Справочник.Справочник1 КАК Справочник1\n" +
        "    |ГДЕ\n" +
        "    |  Справочник1.Ссылка = &Параметр\");\n" +
        "Запрос1.УстановитьПараметр(\"Параметр\", Ссылка);\n" +
        "  РезультатЗапроса = Запрос1.Выполнить();\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testQueryTextNoError() {
    var sample =
      "    Запрос4 = Новый Запрос;\n" +
      "    Запрос4.Текст = \"ВЫБРАТЬ\n" +
      "        |    Справочник1.Ссылка КАК Ссылка\n" +
      "        |ИЗ\n" +
      "        |    Справочник.Справочник1 КАК Справочник1\n" +
      "        |ГДЕ\n" +
      "        |    Справочник1.Ссылка = &Параметр\"; // нет ошибки\n" +
      "    Запрос4.УстановитьПараметр(\"Параметр\", Ссылка);\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }
}
