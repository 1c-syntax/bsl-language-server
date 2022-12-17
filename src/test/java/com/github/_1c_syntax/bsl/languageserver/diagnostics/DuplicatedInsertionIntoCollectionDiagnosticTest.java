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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class DuplicatedInsertionIntoCollectionDiagnosticTest extends AbstractDiagnosticTest<DuplicatedInsertionIntoCollectionDiagnostic> {
  DuplicatedInsertionIntoCollectionDiagnosticTest() {
    super(DuplicatedInsertionIntoCollectionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasIssueOnRange(8, 4, 8, 34,
        getMessage("\"Ключ1\"", "Коллекция"),
        Arrays.asList(
          Ranges.create(7, 4, 34),
          Ranges.create(8, 4, 34)))

      .hasIssueOnRange(12, 4, 35,
        getMessage("\"Ключ1\"", "Коллекция2"),
        Arrays.asList(
          Ranges.create(11, 4, 35),
          Ranges.create(12, 4, 35)))

      .hasIssueOnRange(4, 4, 34,
        getMessage("СтрокаТаблицы", "Массив"),
        Arrays.asList(
          Ranges.create(3, 4, 34),
          Ranges.create(4, 4, 34)))

      .hasIssueOnRange(22, 8, 38,
        getMessage("\"Ключ1\"", "Коллекция"),
        Arrays.asList(
          Ranges.create(21, 8, 38),
          Ranges.create(22, 8, 38)))

      .hasIssueOnRange(27, 8, 55,
        getMessage("\"Пользователь\"", "Итог.Коллекция.Индексы"),
        Arrays.asList(
          Ranges.create(26, 8, 55),
          Ranges.create(27, 8, 55)))

      .hasIssueOnRange(58, 12, 76,
        getMessage("ЭлементСтиля.Ключ", "ЭлементыСтиля"),
        Arrays.asList(
          Ranges.create(56, 12, 87),
          Ranges.create(58, 12, 76)))

      .hasIssueOnRange(99, 8, 77,
        getMessage("\"Пользователь\"", "Данные.Метод().ПовторнаяСоздаваемаяКоллекция"),
        Arrays.asList(
          Ranges.create(98, 8, 77),
          Ranges.create(99, 8, 77)))

      .hasIssueOnRange(102, 8, 92,
        getMessage("Данные.Метод().ПовторнаяСоздаваемаяКоллекция", "Данные.Метод().ОбщаяКоллекция"),
        Arrays.asList(
          Ranges.create(101, 8, 92),
          Ranges.create(102, 8, 92)))

      .hasIssueOnRange(119, 4, 65,
        getMessage("\"ДополнительныеРеквизиты\"", "ВидыСвойствНабора"),
        Arrays.asList(
          Ranges.create(112, 4, 63),
          Ranges.create(119, 4, 65)))

      .hasIssueOnRange(133, 4, 58,
        getMessage("\"Пользователь\"", "ПовторнаяСоздаваемаяКоллекция"),
        Arrays.asList(
          Ranges.create(132, 4, 58),
          Ranges.create(133, 4, 58)))

      .hasIssueOnRange(136, 4, 58,
        getMessage("ПовторнаяСоздаваемаяКоллекция", "ОбщаяКоллекция"),
        Arrays.asList(
          Ranges.create(135, 4, 58),
          Ranges.create(136, 4, 58)))

      .hasIssueOnRange(147, 8, 90,
        getMessage("Данные2.Реквизит2.ПовторнаяСоздаваемаяКоллекция2", "Данные2.ОбщаяКоллекция2"),
        Arrays.asList(
          Ranges.create(145, 8, 90),
          Ranges.create(147, 8, 90)))

      .hasIssueOnRange(151, 8, 90,
        getMessage("Данные3.Реквизит3.ПовторнаяСоздаваемаяКоллекция3", "Данные3.ОбщаяКоллекция3"),
        Arrays.asList(
          Ranges.create(149, 8, 90),
          Ranges.create(151, 8, 90)))

      .hasIssueOnRange(157, 4, 27,
        getMessage("Ключ", "Описания"),
        Arrays.asList(
          Ranges.create(155, 4, 27),
          Ranges.create(157, 4, 27)))

      .hasIssueOnRange(161, 4, 37,
        getMessage("Часть1.Часть2", "Описания2"),
        Arrays.asList(
          Ranges.create(159, 4, 37),
          Ranges.create(161, 4, 37),
          Ranges.create(162, 4, 37)
        ))

      .hasIssueOnRange(171, 4, 65,
        getMessage("ИмяКоманды", "Сведения2.ДобавленныеЭлементы"),
        Arrays.asList(
          Ranges.create(170, 4, 57),
          Ranges.create(171, 4, 65)
        ))

      .hasIssueOnRange(265, 4, 39,
        getMessage("СтрокаТаблицы", "Коллекция()"),
        Arrays.asList(
          Ranges.create(264, 4, 39),
          Ranges.create(265, 4, 39)
        ))

      .hasIssueOnRange(268, 4, 50,
        getMessage("СтрокаТаблицы2", "Коллекция2().Реквизит"),
        Arrays.asList(
          Ranges.create(267, 4, 50),
          Ranges.create(268, 4, 50)
        ))
    ;

    assertThat(diagnostics).hasSize(18);
  }

  @Test
  void testWithAdd() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("isAllowedMethodADD", true);
    diagnosticInstance.configure(configuration);

    test();
  }

  @Test
  void testWithoutAdd() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("isAllowedMethodADD", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasIssueOnRange(8, 4, 8, 34,
        getMessage("\"Ключ1\"", "Коллекция"),
        Arrays.asList(
          Ranges.create(7, 4, 34),
          Ranges.create(8, 4, 34)))

      .hasIssueOnRange(12, 4, 35,
        getMessage("\"Ключ1\"", "Коллекция2"),
        Arrays.asList(
          Ranges.create(11, 4, 35),
          Ranges.create(12, 4, 35)))

      .hasIssueOnRange(22, 8, 38,
        getMessage("\"Ключ1\"", "Коллекция"),
        Arrays.asList(
          Ranges.create(21, 8, 38),
          Ranges.create(22, 8, 38)))

      .hasIssueOnRange(58, 12, 76,
        getMessage("ЭлементСтиля.Ключ", "ЭлементыСтиля"),
        Arrays.asList(
          Ranges.create(56, 12, 87),
          Ranges.create(58, 12, 76)))

      .hasIssueOnRange(119, 4, 65,
        getMessage("\"ДополнительныеРеквизиты\"", "ВидыСвойствНабора"),
        Arrays.asList(
          Ranges.create(112, 4, 63),
          Ranges.create(119, 4, 65)))

      .hasIssueOnRange(147, 8, 90,
        getMessage("Данные2.Реквизит2.ПовторнаяСоздаваемаяКоллекция2", "Данные2.ОбщаяКоллекция2"),
        Arrays.asList(
          Ranges.create(145, 8, 90),
          Ranges.create(147, 8, 90)))

      .hasIssueOnRange(151, 8, 90,
        getMessage("Данные3.Реквизит3.ПовторнаяСоздаваемаяКоллекция3", "Данные3.ОбщаяКоллекция3"),
        Arrays.asList(
          Ranges.create(149, 8, 90),
          Ranges.create(151, 8, 90))
      );

    assertThat(diagnostics).hasSize(7);
  }

  private String getMessage(String keyName, String collectionName) {
    return String.format("Проверьте повторную вставку %s в коллекцию %s", keyName, collectionName);
  }
}
