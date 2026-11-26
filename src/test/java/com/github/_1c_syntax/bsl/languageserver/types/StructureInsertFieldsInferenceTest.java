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
 * Поля «открытой» {@code Структура}, накопленные через {@code X.Вставить(...)},
 * должны типизировать обращения {@code X.Поле}.
 */
@CleanupContextBeforeClassAndAfterClass
class StructureInsertFieldsInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void structureFieldsAccumulatedFromInsertCalls() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/StructureInsertFields.bsl");

    var nameTypes = inferAtMarker(documentContext, "X = Параметры.Имя", "X = Параметры.".length() + 1);
    assertThat(nameTypes.refs())
      .as("Параметры.Имя ← Вставить(\"Имя\", \"Иван\") → Строка")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");

    var ageTypes = inferAtMarker(documentContext, "Y = Параметры.Возраст", "Y = Параметры.".length() + 1);
    assertThat(ageTypes.refs())
      .as("Параметры.Возраст ← Вставить(\"Возраст\", 42) → Число")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Число");
  }

  @Test
  void structureInsertWithOnlyKeyGivesUndefinedValue() {
    // Параметры.Вставить("ТолькоКлюч") (без value-arg) → ключ с типом Неопределено.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/StructureInsertFields.bsl");

    var types = inferAtMarker(documentContext, "Z = Параметры.ТолькоКлюч",
      "Z = Параметры.".length() + 1);
    assertThat(types.refs())
      .extracting(r -> r.qualifiedName())
      .containsExactly("Неопределено");
  }

  @Test
  void structureInsertWithEmptyParensDoesNotThrow() {
    // given: в области видимости есть незавершённый вызов Параметры.Вставить() без аргументов.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/StructureInsertFields.bsl");

    // when: выводим типы поля структуры, накопленные через корректные Вставить(...).
    var nameTypes = inferAtMarker(documentContext, "X = Параметры.Имя", "X = Параметры.".length() + 1);

    // then: пустые скобки не приводят к NPE, корректные поля по-прежнему типизируются.
    assertThat(nameTypes.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");
  }

  private TypeSet inferAtMarker(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine));
  }
}
