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
//      .hasMessageOnRange("Добавьте установку параметра запроса \"Параметр1\"", 7, 34, 44)
//      .hasMessageOnRange("Добавьте установку параметра запроса \"Параметр3\"", 35, 34, 44)
//      .hasMessageOnRange("Добавьте установку параметра запроса \"Параметр5\"", 63, 34, 44)
      .hasSize(3)
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
      "  Запрос2 = Новый Запрос(\n" +
        "    \"ВЫБРАТЬ\n" +
        "    |  Справочник2.Ссылка КАК Ссылка\n" +
        "    |ИЗ\n" +
        "    |  Справочник.Справочник2 КАК Справочник2\n" +
        "    |ГДЕ\n" +
        "    |  Справочник2.Ссылка = &Параметр2\");\n" +
        "Запрос2.УстановитьПараметр(\"Параметр2\", Ссылка);\n" +
        "  РезультатЗапроса = Запрос2.Выполнить();\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testQueryTextNoError() {
    var sample =
      "Процедура Четвертая_НетОшибки_ЗапросТекст()\n" +
        "\n" +
        "    Запрос4 = Новый Запрос;\n" +
        "    Запрос4.Текст = \"ВЫБРАТЬ\n" +
        "        |    Справочник4.Ссылка КАК Ссылка\n" +
        "        |ИЗ\n" +
        "        |    Справочник.Справочник4 КАК Справочник4\n" +
        "        |ГДЕ\n" +
        "        |    Справочник4.Ссылка = &Параметр4\"; // нет ошибки\n" +
        "\n" +
        "    Запрос4.УстановитьПараметр(\"Параметр4\", Ссылка);\n" +
        "\n" +
        "    РезультатЗапроса = Запрос4.Выполнить();\n" +
        "КонецПроцедуры\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testQueryTextWithoutNewQuery() {
    var sample =
      "Процедура Шестой_ТекстБезСозданияЗапроса()\n" +
        "    НеиспользуемыйТекст6 = \"ВЫБРАТЬ\n" +
        "        |    Справочник6.Ссылка КАК Ссылка\n" +
        "        |ИЗ\n" +
        "        |    Справочник.Справочник6 КАК Справочник6\n" +
        "        |ГДЕ\n" +
        "        |    Справочник6.Ссылка = &Параметр6\"; // не ошибка\n" +
//        "\n" +
//        "    Запрос6 = Новый Запрос(Текст);\n" +
//        "    РезультатЗапроса = Запрос6.Выполнить();\n" +
//        "\n" +
        "КонецПроцедуры\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testQueryTextWithNewQueryForOtherText() {
    var sample =
      "Процедура Седьмой_СначалаТекстПотомЗапросСДругимТекстом(Текст)\n" +
        "    НеиспользуемыйТекст7 = \"ВЫБРАТЬ\n" +
        "        |    Справочник7.Ссылка КАК Ссылка\n" +
        "        |ИЗ\n" +
        "        |    Справочник.Справочник7 КАК Справочник7\n" +
        "        |ГДЕ\n" +
        "        |    Справочник7.Ссылка = &Параметр7\"; // не ошибка\n" +
        "    Запрос7 = Новый Запрос(Текст);\n" +
//        "    //РезультатЗапроса = Запрос7.Выполнить();\n" +
        "КонецПроцедуры\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testQueryTextThenNewQuery() {
    var sample =
      "Процедура ПятаяОшибка_СначалаТекстПотомНовыйЗапрос()\n" +
        "\n" +
        "    Текст5 = \"ВЫБРАТЬ\n" +
        "        |    Справочник1.Ссылка КАК Ссылка\n" +
        "        |ИЗ\n" +
        "        |    Справочник.Справочник1 КАК Справочник1\n" +
        "        |ГДЕ\n" +
        "        |    Справочник1.Ссылка = &Параметр5\"; // ошибка\n" +
        "    Запрос5 = Новый Запрос(Текст5);\n" +
//        "    РезультатЗапроса = Запрос5.Выполнить();\n" +
        "КонецПроцедуры\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
  }
}
