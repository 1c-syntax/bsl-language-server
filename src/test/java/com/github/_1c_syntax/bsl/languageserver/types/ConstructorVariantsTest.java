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
 * Конструкторы платформенных типов с разной арностью и аргументами.
 */
@CleanupContextBeforeClassAndAfterClass
class ConstructorVariantsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void arrayNoArgs() {
    var t = at("М1 = Новый Массив", "М1 = ".length());
    assertThat(qnames(t)).contains("Массив");
  }

  @Test
  void arrayEmptyParens() {
    var t = at("М2 = Новый Массив()", "М2 = ".length());
    assertThat(qnames(t)).contains("Массив");
  }

  @Test
  void arrayWithCount() {
    var t = at("М3 = Новый Массив(10)", "М3 = ".length());
    assertThat(qnames(t)).contains("Массив");
  }

  @Test
  void arrayMultidim() {
    var t = at("М4 = Новый Массив(3, 5)", "М4 = ".length());
    assertThat(qnames(t)).contains("Массив");
  }

  @Test
  void arrayFromOtherArray() {
    var t = at("М5 = Новый Массив(М1)", "М5 = ".length());
    assertThat(qnames(t)).contains("Массив");
  }

  @Test
  void structureEmpty() {
    var t = at("С1 = Новый Структура", "С1 = ".length());
    assertThat(qnames(t)).contains("Структура");
  }

  @Test
  void structureWithSingleKey() {
    var t = at("С3 = Новый Структура(\"Имя\")", "С3 = ".length());
    assertThat(qnames(t)).contains("Структура");
  }

  @Test
  void structureWithKeyValue() {
    var t = at("С4 = Новый Структура(\"Имя\", \"значение\")", "С4 = ".length());
    assertThat(qnames(t)).contains("Структура");
  }

  @Test
  void mapNoArgs() {
    var t = at("Со1 = Новый Соответствие", "Со1 = ".length());
    assertThat(qnames(t)).contains("Соответствие");
  }

  @Test
  void fixedStructureFromStructure() {
    var t = at("ФС = Новый ФиксированнаяСтруктура(С5)", "ФС = ".length());
    assertThat(qnames(t)).contains("ФиксированнаяСтруктура");
  }

  @Test
  void uuidNoArgs() {
    var t = at("УИД1 = Новый УникальныйИдентификатор", "УИД1 = ".length());
    assertThat(qnames(t)).contains("УникальныйИдентификатор");
  }

  @Test
  void uuidWithStringArg() {
    var t = at("УИД2 = Новый УникальныйИдентификатор(\"00000000-0000-0000-0000-000000000000\")",
      "УИД2 = ".length());
    assertThat(qnames(t)).contains("УникальныйИдентификатор");
  }

  @Test
  void valueListNoArgs() {
    var t = at("СЗ = Новый СписокЗначений", "СЗ = ".length());
    assertThat(qnames(t)).contains("СписокЗначений");
  }

  @Test
  void typeDescriptionNoArgs() {
    var t = at("ОТ1 = Новый ОписаниеТипов", "ОТ1 = ".length());
    assertThat(qnames(t)).contains("ОписаниеТипов");
  }

  @Test
  void xmlWriterNoArgs() {
    var t = at("ЗапXML = Новый ЗаписьXML", "ЗапXML = ".length());
    assertThat(qnames(t)).contains("ЗаписьXML");
  }

  @Test
  void jsonWriterNoArgs() {
    var t = at("ЗапJSON = Новый ЗаписьJSON", "ЗапJSON = ".length());
    assertThat(qnames(t)).contains("ЗаписьJSON");
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
      "./src/test/resources/types/ConstructorVariants.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
