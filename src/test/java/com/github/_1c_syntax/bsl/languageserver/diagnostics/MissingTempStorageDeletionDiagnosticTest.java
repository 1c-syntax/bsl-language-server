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

class MissingTempStorageDeletionDiagnosticTest extends AbstractDiagnosticTest<MissingTempStorageDeletionDiagnostic> {
  MissingTempStorageDeletionDiagnosticTest() {
    super(MissingTempStorageDeletionDiagnostic.class);
  }

  @Test
  void test() {

    var diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasSize(4)
      .hasRange(3, 24, 77)
      .hasRange(13, 24, 77)
      .hasRange(21, 24, 77)
      .hasRange(33, 24, 77)
    ;

  }

  @Test
  void testFileCodeBlock() {

    var diagnostics = getDiagnosticList(
      "ПодобранныеТоварыТело = ПолучитьИзВременногоХранилища(АдресТоваровВХранилищеТело);");

    assertThat(diagnostics, true)
      .hasRange(0, 24, 81)
      .hasSize(1)
    ;

  }

  @Test
  void testFileCodeBlockWithoutError() {

    var diagnostics = getDiagnosticList(
      """
        ПодобранныеТоварыТело2 = ПолучитьИзВременногоХранилища(АдресТоваровВХранилищеТело2); // не ошибка
        Объект.Товары.Загрузить(ПодобранныеТоварыТело2);
        УдалитьИзВременногоХранилища(АдресТоваровВХранилищеТело2)""");

    assertThat(diagnostics).isEmpty();

  }

  @Test
  void testTryBlockWithoutError() {

    var diagnostics = getDiagnosticList(
      """
        &НаСервере
        Процедура ПолучитьТоварыИзХранилища_Успешно1()

            Адрес = "";
            Попытка
                ОбщийМодуль.ПолучитьАдрес(Адрес);

                ПодобранныеТовары = ПолучитьИзВременногоХранилища(Адрес); // не ошибка
                Результат = ПодобранныеТовары.ВыгрузитьКолонку("Наименование");

                УдалитьИзВременногоХранилища(Адрес);
            Исключение
            КонецПопытки;

        КонецПроцедуры""");

    assertThat(diagnostics).isEmpty();

  }

  @Test
  void testFileBlockBeforeSubForTester() {

    var diagnostics = getDiagnosticList(
      """
        Данные = ПолучитьИзВременногоХранилища( тут.Адрес );
        Данные.Записать ( "c:\\mydata.txt" );
        &НаСервере
        Процедура Обработать ()
        КонецПроцедуры""");

    assertThat(diagnostics, true)
      .hasRange(0, 9, 51)
      .hasSize(1)
    ;

  }

  @Test
  void testTryFileBlockBeforeSubForTesterWithoutError() {

    var diagnostics = getDiagnosticList(
      """
        Данные = ПолучитьИзВременногоХранилища( тут.Адрес );
        Данные.Записать ( "c:\\mydata.txt" );
        УдалитьИзВременногоХранилища( тут.Адрес );
        &НаСервере
        Процедура Обработать ()
        КонецПроцедуры""");

    assertThat(diagnostics).isEmpty();

  }

  private List<Diagnostic> getDiagnosticList(String s) {

    var documentContext = TestUtils.getDocumentContext(s);
    return getDiagnostics(documentContext);
  }
}
