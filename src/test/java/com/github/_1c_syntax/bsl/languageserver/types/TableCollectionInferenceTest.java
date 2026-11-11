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
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Результат членов табличных коллекций зависит от колонок получателя и аргументов
 * вызова, поэтому уточняется на месте, а не берётся из объявленного типа.
 */
@CleanupContextBeforeClassAndAfterClass
class TableCollectionInferenceTest extends AbstractServerContextAwareTest {

  private static final String MODULE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";

  private static final String SECTION_ROW =
    "ДокументТабличнаяЧастьСтрока.Документ1.ТабличнаяЧасть1";

  @Autowired
  private TypeService typeService;

  @Test
  void unloadedSectionKeepsOnlyRequestedColumns() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Документ = Документы.Документ1.СоздатьДокумент();
        ВсеКолонки = Документ.ТабличнаяЧасть1.Выгрузить();
        ОднаКолонка = Документ.ТабличнаяЧасть1.Выгрузить(, "Реквизит1");
      КонецПроцедуры
      """);

    assertThat(columnsOf(documentContext, "ВсеКолонки"))
      .as("без списка колонок выгружается вся табличная часть")
      .contains("Реквизит1", "Реквизит2");
    assertThat(columnsOf(documentContext, "ОднаКолонка"))
      .as("список колонок задан литералом — в таблице только он")
      .containsExactly("Реквизит1");
  }

  @Test
  void unloadColumnsTakesNamesFromFirstArgument() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Документ = Документы.Документ1.СоздатьДокумент();
        Часть = Документ.ТабличнаяЧасть1.ВыгрузитьКолонки("Реквизит2");
      КонецПроцедуры
      """);

    assertThat(columnsOf(documentContext, "Часть"))
      .as("у ВыгрузитьКолонки список колонок — первый параметр")
      .containsExactly("Реквизит2");
  }

  @Test
  void unloadColumnIsTypedByThatColumn() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Документ = Документы.Документ1.СоздатьДокумент();
        Значения = Документ.ТабличнаяЧасть1.ВыгрузитьКолонку("Реквизит1");
      КонецПроцедуры
      """);

    var types = typesOf(documentContext, "Значения");
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Массив");
    assertThat(types.getElementTypes().refs()).extracting(TypeRef::qualifiedName)
      .as("массив типизируется типом самой колонки")
      .containsExactly(rowColumnType(documentContext));
  }

  @Test
  void columnsCollectionExposesEachColumn() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Документ = Документы.Документ1.СоздатьДокумент();
        Колонки = Документ.ТабличнаяЧасть1.Выгрузить().Колонки;
      КонецПроцедуры
      """);

    var types = typesOf(documentContext, "Колонки");
    assertThat(types.refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("КоллекцияКолонокТаблицыЗначений");
    assertThat(types.getLocalFields(types.refs().iterator().next()))
      .as("каждая колонка таблицы — своё свойство коллекции колонок")
      .containsKeys("Реквизит1", "Реквизит2");
  }

  @Test
  void columnsCollectionThroughVariable() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Документ = Документы.Документ1.СоздатьДокумент();
        ТЗ = Документ.ТабличнаяЧасть1.Выгрузить();
        Колонки = ТЗ.Колонки;
      КонецПроцедуры
      """);

    var types = typesOf(documentContext, "Колонки");
    assertThat(types.getLocalFields(types.refs().iterator().next()))
      .as("через переменную состав колонок теряться не должен")
      .containsKeys("Реквизит1", "Реквизит2");
  }

  @Test
  void columnsCollectionOfLocalValueTable() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        ТЗ = Новый ТаблицаЗначений;
        ТЗ.Колонки.Добавить("Цена", Новый ОписаниеТипов("Число"));
        ТЗ.Колонки.Добавить("Товар", Новый ОписаниеТипов("Строка"));
        Колонки = ТЗ.Колонки;
      КонецПроцедуры
      """);

    var types = typesOf(documentContext, "Колонки");
    assertThat(types.getLocalFields(types.refs().iterator().next()))
      .as("колонки локальной таблицы значений накоплены по Колонки.Добавить")
      .containsKeys("Цена", "Товар");
  }

  @Test
  void unloadedColumnElementIsReachableThroughGet() {
    // Репорт: в цепочке ….ВыгрузитьКолонку("Реквизит1").Получить(0) переменная
    // получала «Произвольный» — платформенный тип возврата Получить().
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Документ = Документы.Документ1.СоздатьДокумент();
        Значение = Документ.ТабличнаяЧасть1.ВыгрузитьКолонку("Реквизит1").Получить(0);
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Значение").refs()).extracting(TypeRef::qualifiedName)
      .as("Получить(Индекс) — методная форма индексатора, тип элемента должен доехать")
      .containsExactly(rowColumnType(documentContext));
  }

  @Test
  void mapGetReturnsValueNotKeyValuePair() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Соответствие = Новый Соответствие;
        Элемент = Соответствие.Получить("Ключ");
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Элемент").refs()).extracting(TypeRef::qualifiedName)
      .as("у Соответствия Получить(Ключ) даёт значение, а не КлючИЗначение")
      .doesNotContain("КлючИЗначение");
  }

  @Test
  void englishMemberNamesAreRefinedToo() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Документ = Документы.Документ1.СоздатьДокумент();
        Часть = Документ.ТабличнаяЧасть1.UnloadColumns("Реквизит1");
        Значения = Документ.ТабличнаяЧасть1.UnloadColumn("Реквизит1");
        Колонки = Документ.ТабличнаяЧасть1.Unload().Columns;
      КонецПроцедуры
      """);

    assertThat(columnsOf(documentContext, "Часть"))
      .as("правило выбирается по дескриптору, поэтому английское написание работает так же")
      .containsExactly("Реквизит1");
    assertThat(typesOf(documentContext, "Значения").getElementTypes().refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly(rowColumnType(documentContext));
    var columns = typesOf(documentContext, "Колонки");
    assertThat(columns.getLocalFields(columns.refs().iterator().next()))
      .containsKeys("Реквизит1", "Реквизит2");
  }

  @Test
  void namesFromVariableLeaveTypeGeneric() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Документ = Документы.Документ1.СоздатьДокумент();
        ИмяКолонки = "Реквизит1";
        Значения = Документ.ТабличнаяЧасть1.ВыгрузитьКолонку(ИмяКолонки);
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Значения").getElementTypes().refs())
      .as("имя собрано в переменной — уточнять нечем, тип элемента остаётся неизвестным")
      .isEmpty();
  }

  /** Тип колонки «Реквизит1» — берём из строки табличной части, чтобы не хардкодить. */
  private String rowColumnType(DocumentContext documentContext) {
    var rowRef = typeService.resolve(SECTION_ROW, documentContext.getFileType()).orElseThrow();
    return typeService.getMembers(rowRef, documentContext.getFileType()).stream()
      .filter(member -> member.matches("Реквизит1"))
      .findFirst()
      .orElseThrow()
      .returnTypes().refs().iterator().next().qualifiedName();
  }

  private DocumentContext moduleWith(String content) {
    initServerContext(PATH_TO_METADATA);
    var uri = Absolute.uri(new File(PATH_TO_METADATA, MODULE));
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext, content, 1);
    return documentContext;
  }

  private java.util.List<String> columnsOf(DocumentContext documentContext, String variable) {
    var types = typesOf(documentContext, variable);
    var element = types.getElementTypes();
    if (element.refs().isEmpty()) {
      return java.util.List.of();
    }
    return java.util.List.copyOf(element.getLocalFields(element.refs().iterator().next()).keySet());
  }

  /** Типы правой части присваивания {@code <var> = …;} — каретка на последнем сегменте цепочки. */
  private TypeSet typesOf(DocumentContext documentContext, String assignedVar) {
    var content = documentContext.getContent();
    var marker = assignedVar + " = ";
    var markerIdx = content.indexOf(marker);
    assertThat(markerIdx).as("маркер `%s` не найден", marker).isGreaterThanOrEqualTo(0);
    var rhsStart = markerIdx + marker.length();
    var statementEnd = content.indexOf(';', rhsStart);
    var caret = lastDotOutsideLiteral(content, rhsStart, statementEnd);
    if (caret < 0) {
      caret = rhsStart;
    } else {
      caret++;
    }
    var lineStart = content.lastIndexOf('\n', caret) + 1;
    var line = content.substring(0, caret).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, caret - lineStart));
  }

  /** Последняя точка вне строкового литерала: точки внутри кавычек — часть аргумента. */
  private static int lastDotOutsideLiteral(String content, int from, int to) {
    var inLiteral = false;
    var lastDot = -1;
    for (var i = from; i < to; i++) {
      var c = content.charAt(i);
      if (c == '"') {
        inLiteral = !inLiteral;
      } else if (c == '.' && !inLiteral) {
        lastDot = i;
      }
    }
    return lastDot;
  }
}
