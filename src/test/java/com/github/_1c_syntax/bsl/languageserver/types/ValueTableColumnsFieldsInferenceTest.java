/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Колонки открытой {@code ТаблицаЗначений}, накопленные через
 * {@code ТЗ.Колонки.Добавить(...)}, должны проявляться как поля строки
 * {@code СтрокаТаблицыЗначений} при обращении через итерацию.
 */
@CleanupContextBeforeClassAndAfterClass
class ValueTableColumnsFieldsInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void columnsBecomeRowFieldsViaForEach() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ValueTableColumnsFields.bsl");

    var nameTypes = inferAtMarker(documentContext, "X = Строка.Имя", "X = Строка.".length() + 1);
    assertThat(nameTypes.refs())
      .as("Строка.Имя — колонка, добавленная Колонки.Добавить(\"Имя\")")
      .isNotEmpty();

    var sumTypes = inferAtMarker(documentContext, "Y = Строка.Сумма", "Y = Строка.".length() + 1);
    assertThat(sumTypes.refs())
      .as("Строка.Сумма — колонка, добавленная Колонки.Добавить(\"Сумма\")")
      .isNotEmpty();
  }

  @Test
  void columnsBecomeRowFieldsViaAddRowAssignment() {
    // Регрессия: НоваяСтрока = ТЗ.Добавить() — возвращаемая строка должна знать про
    // колонки, добавленные через ТЗ.Колонки.Добавить("X"), так же как `Для Каждого`-строка.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ValueTableColumnsFields.bsl");

    var nameTypes = inferAtMarker(documentContext, "A = НоваяСтрока.Имя", "A = НоваяСтрока.".length() + 1);
    assertThat(nameTypes.refs())
      .as("НоваяСтрока.Имя — колонка должна быть видна на строке, полученной из ТЗ.Добавить()")
      .isNotEmpty();

    var sumTypes = inferAtMarker(documentContext, "B = НоваяСтрока.Сумма", "B = НоваяСтрока.".length() + 1);
    assertThat(sumTypes.refs())
      .as("НоваяСтрока.Сумма — колонка должна быть видна на строке, полученной из ТЗ.Добавить()")
      .isNotEmpty();
  }

  private TypeSet inferAtMarker(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.inferAtPosition(documentContext, new Position(line, charInLine));
  }
}
