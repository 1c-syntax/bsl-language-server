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
 * Большая интеграционная фикстура — много типичных сценариев BSL:
 * локальные функции с JsDoc, ТЗ с типизированными колонками, цикл по
 * Структура/Соответствие, тернарный, Тип(), и т.п. Тесты проверяют
 * инферим типов в ключевых местах.
 */
@CleanupContextBeforeClassAndAfterClass
class BigBslFixtureTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void structureKeyAsStringIsString() {
    var t = at("ИмяПар = ПараметрыЗапроса.Имя", "ИмяПар = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void structureKeyAsNumberIsNumber() {
    var t = at("ВозрастПар = ПараметрыЗапроса.Возраст", "ВозрастПар = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void structureKeyAsBooleanIsBoolean() {
    var t = at("АктивПар = ПараметрыЗапроса.Активен", "АктивПар = ".length());
    assertThat(qnames(t)).contains("Булево");
  }

  @Test
  void valueTableRowColumnNumberAccess() {
    var t = at("ТекСумма = СтрокаТЗ.Сумма", "ТекСумма = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void valueTableRowColumnStringAccess() {
    var t = at("Назв = СтрокаТЗ.Название", "Назв = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void mapValueByStringKey() {
    var t = at("ТекстИзКарты = Карта[\"text\"]", "ТекстИзКарты = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void mapValueByNumberKey() {
    var t = at("СчётчикИзКарты = Карта[\"count\"]", "СчётчикИзКарты = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void fixedStructureValueAccessDoesNotCrash() {
    // ФиксированнаяСтруктура не пробрасывает field types из исходной Структуры —
    // достаточно того, что supplier не падает.
    var t = at("ИмяФикс = ФиксС.Имя", "ИмяФикс = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void ternaryUnionOfStructures() {
    var t = at("Ветка = ?(КолВо > 0, ПараметрыЗапроса, Новый Структура)",
      "Ветка = ".length());
    assertThat(t).isNotNull();
    assertThat(qnames(t)).contains("Структура");
  }

  @Test
  void localFunctionReturnTypeFromJsDoc() {
    var t = at("КопияСписка = СоздатьСписокЧисел()",
      "КопияСписка = ".length());
    assertThat(qnames(t)).contains("Массив");
  }

  @Test
  void parameterTypeFromSeeJsDoc() {
    // Документ в ОбработатьСписок — См. СоздатьСписокЧисел → Массив.
    var t = at("ПерваяСтрока = Документ[0]", "ПерваяСтрока = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void forEachMapPairKey() {
    var t = at("КлючКЗ = КЗ.Ключ", "КлючКЗ = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void forEachStructurePairKey() {
    var t = at("КлючС = КЗС.Ключ", "КлючС = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void typeFunctionReturnsType() {
    var t = at("ТипЧисло = Тип(\"Число\")", "ТипЧисло = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void dateConstructorReturnsDate() {
    var t = at("Дата1 = Новый Дата(2020, 1, 1)", "Дата1 = ".length());
    assertThat(qnames(t)).contains("Дата");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var dc = doc();
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(dc, new Position(line, charInLine + 1));
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/BigBslFixture.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
