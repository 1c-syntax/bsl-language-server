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
  void columnsFromDescriptionBecomeRowFields() {
    // given: колонки таблицы объявлены в описании возвращаемого значения функции,
    // а не собраны рядом через «Колонки.Добавить».
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ValueTableColumnsFields.bsl");

    // when
    var idTypes = inferAtMarker(documentContext,
      "C = СтрокаПоОписанию.Идентификатор", "C = СтрокаПоОписанию.".length() + 1);
    var sumTypes = inferAtMarker(documentContext,
      "D = СтрокаПоОписанию.Сумма", "D = СтрокаПоОписанию.".length() + 1);

    // then: строка из «Добавить()» знает колонки так же, как при сборке их по коду.
    assertThat(idTypes.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");
    assertThat(sumTypes.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Число");
  }

  @Test
  void columnsFromDescriptionBecomeRowFieldsViaForEach() {
    // given: та же таблица из описания, обход «Для Каждого».
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ValueTableColumnsFields.bsl");

    // when
    var idTypes = inferAtMarker(documentContext,
      "E = ОбходПоОписанию.Идентификатор", "E = ОбходПоОписанию.".length() + 1);

    // then
    assertThat(idTypes.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");
  }

  @Test
  void treeColumnsFromDescriptionBecomeRowFields() {
    // given: колонки дерева объявлены в описании возвращаемого значения функции.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ValueTableColumnsFields.bsl");

    // when
    var types = inferAtMarker(documentContext,
      "F = СтрокаДерева.Наименование", "F = СтрокаДерева.".length() + 1);

    // then
    assertThat(types.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");
  }

  @Test
  void mapFieldsFromDescriptionBecomeElementFields() {
    // given: у соответствия описаны «Ключ» и «Значение» — это свойства элемента
    // «КлючИЗначение», а не самого соответствия: обращения к нему через точку в 1С нет.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ValueTableColumnsFields.bsl");

    // when
    var keyTypes = inferAtMarker(documentContext,
      "G = ЭлементСоответствия.Ключ", "G = ЭлементСоответствия.".length() + 1);
    var valueTypes = inferAtMarker(documentContext,
      "H = ЭлементСоответствия.Значение", "H = ЭлементСоответствия.".length() + 1);

    // then
    assertThat(keyTypes.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");
    assertThat(valueTypes.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Число");
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
  void columnTypeExtractedThroughIntermediateVariable() {
    // Регрессия на чисто-инференсерный подход: если ОписаниеТипов попало в переменную,
    // тип колонки всё равно должен выводиться (TypeSet переменной несёт elementTypes
    // от своего constructor-инициализатора).
    var content = """
      Процедура Тест()
      ОТ = Новый ОписаниеТипов("Число");
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("Поле", ОТ);
      Для Каждого Строка Из ТЗ Цикл
      А = Строка.Поле;
      КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "А = Строка.Поле", "А = Строка.".length() + 1);
    assertThat(refNames(types))
      .as("ОписаниеТипов, положенное в переменную, всё равно несёт типы — инференсер видит")
      .contains("Число");
  }

  @Test
  void columnTypeIgnoredWhenSecondArgIsNotTypeDescription() {
    // По сигнатуре платформы 2-й аргумент Колонки.Добавить — объект ОписаниеТипов.
    // Если передано что-то иное (Тип(...) или строка) — типы НЕ извлекаются,
    // колонка остаётся со значением по умолчанию (Неопределено).
    var content = """
      Процедура Тест()
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("ПолеИзТипа", Тип("Число"));
      ТЗ.Колонки.Добавить("ПолеИзСтроки", "Число");
      Для Каждого Строка Из ТЗ Цикл
      A = Строка.ПолеИзТипа;
      B = Строка.ПолеИзСтроки;
      КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var fromType = inferAtMarker(documentContext, "A = Строка.ПолеИзТипа", "A = Строка.".length() + 1);
    assertThat(refNames(fromType))
      .as("Тип(\"Число\") не является ОписаниеТипов — типы колонки не выводим")
      .containsExactly("Неопределено");

    var fromString = inferAtMarker(documentContext, "B = Строка.ПолеИзСтроки", "B = Строка.".length() + 1);
    assertThat(refNames(fromString))
      .as("строковый литерал не является ОписаниеТипов — типы колонки не выводим")
      .containsExactly("Неопределено");
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
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine));
  }
}
