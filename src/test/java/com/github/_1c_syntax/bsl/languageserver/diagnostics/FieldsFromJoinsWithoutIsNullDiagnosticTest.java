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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class FieldsFromJoinsWithoutIsNullDiagnosticTest extends AbstractDiagnosticTest<FieldsFromJoinsWithoutIsNullDiagnostic> {
  FieldsFromJoinsWithoutIsNullDiagnosticTest() {
    super(FieldsFromJoinsWithoutIsNullDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(9);

    checkContent(
      diagnostics.get(0),
      Ranges.create(6, 5, 7, 44),
      Ranges.create(4, 13, 4, 30)
    );

    checkContent(
      diagnostics.get(1),
      Ranges.create(20, 5, 21, 45),
      Ranges.create(16, 13, 16, 31)
    );

    checkContent(
      diagnostics.get(2),
      Ranges.create(33, 5, 34, 45),
      Ranges.create(30, 13, 30, 31)
    );

    checkContent(
      diagnostics.get(3),
      Ranges.create(45, 5, 46, 45),
      Ranges.create(47, 9, 47, 25)
    );

    checkContent(
      diagnostics.get(4),
      Ranges.create(60, 5, 61, 46),
      Ranges.create(57, 13, 57, 27)
    );

    checkContent(
      diagnostics.get(5),
      Ranges.create(84, 5, 85, 46),
      Ranges.create(87, 8, 87, 26)
    );

    checkContent(
      diagnostics.get(6),
      Ranges.create(104, 5, 105, 46),
      Arrays.asList(
        Ranges.create(99, 5, 99, 19),
        Ranges.create(98, 13, 98, 31),
        Ranges.create(100, 5, 100, 28))
    );

    checkContent(
      diagnostics.get(7),
      Ranges.create(154, 8, 155, 50),
      Ranges.create(151, 8, 151, 28)
    );

    checkContent(
      diagnostics.get(8),
      Ranges.create(194, 5, 195, 50),
      Arrays.asList(
        Ranges.create(192, 13, 32),
        Ranges.create(196, 9, 30)
      ));

  }

  private void checkContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    Range relatedLocationRange
  ) {
    checkContent(diagnostic, diagnosticRange, Collections.singletonList(relatedLocationRange));
  }

  private void checkContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    List<Range> relatedLocationRanges
  ) {
    assertThat(diagnostic.getRange()).isEqualTo(diagnosticRange);
    List<DiagnosticRelatedInformation> relatedInformationList = diagnostic.getRelatedInformation();
    assertThat(relatedInformationList).hasSize(relatedLocationRanges.size());

    for (int i = 0; i < relatedLocationRanges.size(); i++) {
      var relatedInformation = relatedInformationList.get(i);
      var relatedLocationRange = relatedLocationRanges.get(i);
      Assertions.assertThat(relatedInformation.getLocation().getRange()).isEqualTo(relatedLocationRange);
    }
  }

  @Test
  void testWithIsNotNullInsideExpression() {
    var sample =
      "    Запрос = Новый Запрос;\n" +
        "    Запрос.Текст =\n" +
        "    \"ВЫБРАТЬ Сотрудники4.Ссылка // не ошибка\n" +
        "    |ИЗ Справочник.Склады КАК Склады\n" +
        "    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники4\n" +
        "    |ПО Склады.Кладовщик = Сотрудники4.Ссылка\n" +
        "    |ГДЕ Сотрудники4.Флаг ЕСТЬ НЕ NULL   //не ошибка\n" +
        "    |\";\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testWithNotIsNullInsideExpression() {
    var sample =
      "    Запрос = Новый Запрос;\n" +
        "    Запрос.Текст =\n" +
        "    \"ВЫБРАТЬ Сотрудники4.Ссылка // не ошибка\n" +
        "    |ИЗ Справочник.Склады КАК Склады\n" +
        "    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники4\n" +
        "    |ПО Склады.Кладовщик = Сотрудники4.Ссылка\n" +
        "    |ГДЕ НЕ Сотрудники4.Флаг ЕСТЬ NULL   //не ошибка\n" +
        "    |\";\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testWithNotIsNullInPairsInsideExpression() {
    var sample =
      "    Запрос = Новый Запрос;\n" +
        "    Запрос.Текст =\n" +
        "    \"ВЫБРАТЬ Сотрудники.Ссылка // не ошибка\n" +
        "    |ИЗ Справочник.Склады КАК Склады\n" +
        "    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники\n" +
        "    |ПО Склады.Кладовщик = Сотрудники.Ссылка\n" +
        "    |ГДЕ (НЕ (Сотрудники.Флаг ЕСТЬ NULL))   //не ошибка\n" +
        "    |\"; ";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testWithIsNullInsideExpression() {
    var sample =
      "    Запрос = Новый Запрос;\n" +
        "    Запрос.Текст =\n" +
        "    \"ВЫБРАТЬ Сотрудники4.Ссылка // ошибка\n" +
        "    |ИЗ Справочник.Склады КАК Склады\n" +
        "    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники4\n" +
        "    |ПО Склады.Кладовщик = Сотрудники4.Ссылка\n" +
        "    |ГДЕ Сотрудники4.Флаг ЕСТЬ NULL   //не ошибка\n" +
        "    |\";\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void tesEqualTableNamesInUnion() {
    var sample =
      "    Запрос = Новый Запрос;\n" +
        "    Запрос.Текст =\n" +
        "    \"ВЫБРАТЬ\n" +
        "    |\tКонтрагенты11.Ссылка КАК Ссылка  //не ошибка \n" +
        "    |ПОМЕСТИТЬ ВТ\n" +
        "    |ИЗ\n" +
        "    |\tТаблица КАК Контрагенты11\n" +
        "    |\n" +
        "    |ОБЪЕДИНИТЬ ВСЕ\n" +
        "    |\n" +
        "    |ВЫБРАТЬ\n" +
        "    |   Таблица11.Ссылка КАК Ссылка,\n" +
        "    |   Контрагенты11.Ссылка КАК Ссылка1    //<-- ошибка \n" +
        "    |ИЗ\n" +
        "    |\tСправочник.Склады КАК Таблица11\n" +
        "    |   ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Контрагенты КАК Контрагенты11\n" +
        "    |   ПО Таблица11.Ссылка = Контрагенты11.Ссылка\n" +
        "    |\";\n";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
  }
}
