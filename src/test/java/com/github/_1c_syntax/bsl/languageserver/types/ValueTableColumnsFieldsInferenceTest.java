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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

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

  @Test
  void columnTypeExtractedFromTypeDescriptionConstructor() {
    // Колонки.Добавить("Имя", Новый ОписаниеТипов("Число")) — колонка должна иметь тип Число.
    var content = """
      Процедура Тест()
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("Имя", Новый ОписаниеТипов("Число"));
      Для Каждого Строка Из ТЗ Цикл
      А = Строка.Имя;
      КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "А = Строка.Имя", "А = Строка.".length() + 1);
    assertThat(refNames(types))
      .as("колонка с Новый ОписаниеТипов(\"Число\") должна давать тип Число")
      .contains("Число");
  }

  @Test
  void columnTypeExtractedFromTypeDescriptionMultiple() {
    // Новый ОписаниеТипов("Число,Строка") — оба типа должны попасть в union.
    var content = """
      Процедура Тест()
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("Поле", Новый ОписаниеТипов("Число,Строка"));
      Для Каждого Строка Из ТЗ Цикл
      А = Строка.Поле;
      КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "А = Строка.Поле", "А = Строка.".length() + 1);
    assertThat(refNames(types))
      .as("мультитип в ОписаниеТипов — union всех заявленных типов")
      .contains("Число", "Строка");
  }

  @Test
  void columnTypeExtractedFromTypeFunctionCall() {
    // Колонки.Добавить("Имя", Тип("Число"))
    var content = """
      Процедура Тест()
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("Поле", Тип("Число"));
      Для Каждого Строка Из ТЗ Цикл
      А = Строка.Поле;
      КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "А = Строка.Поле", "А = Строка.".length() + 1);
    assertThat(refNames(types))
      .as("Тип(\"Число\") как тип колонки — резолвится в Число")
      .contains("Число");
  }

  @Test
  void columnTypeFromStringLiteralArg() {
    // Колонки.Добавить("Поле", "Число,Строка") — реже, но допустимый формат
    var content = """
      Процедура Тест()
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("Поле", "Число,Строка");
      Для Каждого Строка Из ТЗ Цикл
      А = Строка.Поле;
      КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "А = Строка.Поле", "А = Строка.".length() + 1);
    assertThat(refNames(types))
      .as("прямой строковый литерал с типами тоже принимается")
      .contains("Число", "Строка");
  }

  @Test
  void columnWithoutTypeArgRetainsUndefined() {
    // Колонки.Добавить("Поле") без 2-го аргумента — fallback Неопределено.
    var content = """
      Процедура Тест()
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("Поле");
      Для Каждого Строка Из ТЗ Цикл
      А = Строка.Поле;
      КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "А = Строка.Поле", "А = Строка.".length() + 1);
    assertThat(refNames(types))
      .as("без указания типа — оставляем Неопределено")
      .contains("Неопределено");
  }

  private static List<String> refNames(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
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
