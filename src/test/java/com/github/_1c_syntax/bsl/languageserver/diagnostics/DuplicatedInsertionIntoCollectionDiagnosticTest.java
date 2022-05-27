/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

class DuplicatedInsertionIntoCollectionDiagnosticTest extends AbstractDiagnosticTest<DuplicatedInsertionIntoCollectionDiagnostic> {
  DuplicatedInsertionIntoCollectionDiagnosticTest() {
    super(DuplicatedInsertionIntoCollectionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    checkContent(
      diagnostics.get(0),
      Ranges.create(8, 4, 8, 34),
      getMessage("\"Ключ1\"", "Коллекция"),
      Arrays.asList(
        Ranges.create(7, 4, 7, 34),
        Ranges.create(8, 4, 8, 34))
    );

    checkContent(
      diagnostics.get(1),
      Ranges.create(12, 4, 12, 35),
      getMessage("\"Ключ1\"", "Коллекция2"),
      Arrays.asList(
        Ranges.create(11, 4, 11, 35),
        Ranges.create(12, 4, 12, 35))
    );

    checkContent(
      diagnostics.get(2),
      Ranges.create(4, 4, 4, 34),
      getMessage("СтрокаТаблицы", "Массив"),
      Arrays.asList(
        Ranges.create(3, 4, 3, 34),
        Ranges.create(4, 4, 4, 34))
    );

    checkContent(
      diagnostics.get(3),
      Ranges.create(22, 8, 22, 38),
      getMessage("\"Ключ1\"", "Коллекция"),
      Arrays.asList(
        Ranges.create(21, 8, 21, 38),
        Ranges.create(22, 8, 22, 38))
    );

    checkContent(
      diagnostics.get(4),
      Ranges.create(27, 8, 27, 55),
      getMessage("\"Пользователь\"", "Итог.Коллекция.Индексы"),
      Arrays.asList(
        Ranges.create(26, 8, 26, 55),
        Ranges.create(27, 8, 27, 55))
    );

    checkContent(
      diagnostics.get(5),
      Ranges.create(58, 12, 58, 76),
      getMessage("ЭлементСтиля.Ключ", "ЭлементыСтиля"),
      Arrays.asList(
        Ranges.create(56, 12, 56, 87),
        Ranges.create(58, 12, 58, 76))
    );

    checkContent(
      diagnostics.get(6),
      Ranges.create(99, 8, 99, 77),
      getMessage("\"Пользователь\"", "Данные.Метод().ПовторнаяСоздаваемаяКоллекция"),
      Arrays.asList(
        Ranges.create(98, 8, 98, 77),
        Ranges.create(99, 8, 99, 77))
    );

    checkContent(
      diagnostics.get(7),
      Ranges.create(102, 8, 102, 92),
      getMessage("Данные.Метод().ПовторнаяСоздаваемаяКоллекция", "Данные.Метод().ОбщаяКоллекция"),
      Arrays.asList(
        Ranges.create(101, 8, 101, 92),
        Ranges.create(102, 8, 102, 92))
    );

    checkContent(
      diagnostics.get(8),
      Ranges.create(119, 4, 119, 65),
      getMessage("\"ДополнительныеРеквизиты\"", "ВидыСвойствНабора"),
      Arrays.asList(
        Ranges.create(112, 4, 112, 63),
        Ranges.create(119, 4, 119, 65))
    );

    checkContent(
      diagnostics.get(9),
      Ranges.create(133, 4, 133, 58),
      getMessage("\"Пользователь\"", "ПовторнаяСоздаваемаяКоллекция"),
      Arrays.asList(
        Ranges.create(132, 4, 132, 58),
        Ranges.create(133, 4, 133, 58))
    );

    checkContent(
      diagnostics.get(10),
      Ranges.create(136, 4, 136, 58),
      getMessage("ПовторнаяСоздаваемаяКоллекция", "ОбщаяКоллекция"),
      Arrays.asList(
        Ranges.create(135, 4, 135, 58),
        Ranges.create(136, 4, 136, 58))
    );

    assertThat(diagnostics).hasSize(11);

  }

  private String getMessage(String keyName, String collectionName) {
    return String.format("Проверьте повторную вставку %s в коллекцию %s", keyName, collectionName);
  }

  @Test
  void newCollectionAssignBetweenDuplications() {
    var code = "        ПовторнаяСоздаваемаяКоллекция = Новый Массив;\n" +
      "        ПовторнаяСоздаваемаяКоллекция.Добавить(1);\n" +
      "        ПовторнаяСоздаваемаяКоллекция = Новый Массив;\n" +
      "        ПовторнаяСоздаваемаяКоллекция.Добавить(1); // не ошибка\n";

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void newCollectionAssignWithDotsBetweenDuplications() {
    var code =
      "        Данные.ПовторнаяСоздаваемаяКоллекция = Новый Массив;\n" +
      "        Данные.ПовторнаяСоздаваемаяКоллекция.Добавить(1);\n" +
      "        Данные.ПовторнаяСоздаваемаяКоллекция = Новый Массив;\n" +
      "        Данные.ПовторнаяСоздаваемаяКоллекция.Добавить(1);\n";

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void newElemAssignBetweenDuplications() {
    var code = "        ПовторнаяСоздаваемаяКоллекция = Новый Массив;\n" +
      "        ОбщаяКоллекция.Добавить(ПовторнаяСоздаваемаяКоллекция);\n" +
      "        ПовторнаяСоздаваемаяКоллекция = Новый Массив;\n" +
      "        ОбщаяКоллекция.Добавить(ПовторнаяСоздаваемаяКоллекция); // не ошибка\n";

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void useAsFunctionParamsBetweenDuplications() {
    var code = "        Контекст.Коллекция.Вставить(1, Парам);\n" +
      "        Метод(Контекст.Коллекция, 1 + Контекст.Коллекция + 2);\n" +
      "        Контекст.Коллекция.Вставить(1, ДругойПарам); // не ошибка или все-таки подозрительно??\n";

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void changeInnerValueBetweenDuplications() {
    var code = "\tОписание.ИмяРеквизита = \"ТабельныйНомер\";\n" +
      "\tОписания.Добавить(Описание);\n" +
      "\tОписание.ИмяРеквизита = \"ДатаПриема\";\n" +
      "\tОписания.Добавить(Описание); // не ошибка\n";

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void useAsGlobalMethodParamsBetweenDuplications() {
    var code =
      "\tСоздатьВТСотрудникиДляВедомостиПоОснованиям(ИмяВТСотрудники);\n" +
      "\tИменаПромежуточныхВТ.Добавить(ИмяВТСотрудники);\n" +
      "\tСоздатьВТСотрудникиДляВедомостиПоМестуРаботы(ИмяВТСотрудники);\n" +
      "\tИменаПромежуточныхВТ.Добавить(ИмяВТСотрудники); // не ошибка\n";

    var context = TestUtils.getDocumentContext(code);
    var diagnostics = getDiagnostics(context);
    assertThat(diagnostics).hasSize(0);
  }

  // дубль следующих методов из кода FieldsFromJoinsWithoutIsNullDiagnosticTest
  // TODO перенести в DiagnosticAssert
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
    checkContent(diagnostic, diagnosticRange, "", relatedLocationRanges);
  }

  private void checkContent(Diagnostic diagnostic, Range diagnosticRange, String message, List<Range> relatedLocationRanges) {
    assertThat(diagnostic.getRange()).isEqualTo(diagnosticRange);
    if (!message.isEmpty()){
      assertThat(diagnostic.getMessage()).isEqualTo(message);
    }
    List<DiagnosticRelatedInformation> relatedInformationList = diagnostic.getRelatedInformation();
    assertThat(relatedInformationList).hasSize(relatedLocationRanges.size());

    for (int i = 0; i < relatedLocationRanges.size(); i++) {
      var relatedInformation = relatedInformationList.get(i);
      var relatedLocationRange = relatedLocationRanges.get(i);
      Assertions.assertThat(relatedInformation.getLocation().getRange()).isEqualTo(relatedLocationRange);
    }
  }
}
