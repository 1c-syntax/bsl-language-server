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
 * Accumulate-аккумуляторы (Структура.Вставить, ТЗ.Колонки.Добавить) с разными
 * типами значений и колонок.
 */
@CleanupContextBeforeClassAndAfterClass
class AccumulatePatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void structureNumberKey() {
    var t = at("А = Стр1.ПростойКлюч", "А = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void structureStringKey() {
    var t = at("Б = Стр1.Строка", "Б = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void structureDateKey() {
    var t = at("В = Стр1.Дата", "В = ".length());
    assertThat(qnames(t)).contains("Дата");
  }

  @Test
  void structureBooleanKey() {
    var t = at("Г = Стр1.Булево", "Г = ".length());
    assertThat(qnames(t)).contains("Булево");
  }

  @Test
  void structureIndexAccessAsKey() {
    var t = at("А2 = Стр1[\"ПростойКлюч\"]", "А2 = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void mapValueByKey() {
    var t = at("Д = Карта[\"К1\"]", "Д = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void mapValueStringByKey() {
    var t = at("Е = Карта[\"К2\"]", "Е = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void mapValueForNonExistentKeyIsEmpty() {
    var t = at("Ж = Карта[\"несуществует\"]", "Ж = ".length());
    assertThat(t.refs()).isEmpty();
  }

  @Test
  void duplicateKeyMergesValues() {
    // ДубКлюч.Вставить("Поле", 1) затем ДубКлюч.Вставить("Поле", "обновлено");
    // accumulateStructureInsertFields union'ит все значения.
    var t = at("З = ДубКлюч.Поле", "З = ".length());
    assertThat(t).isNotNull();
    assertThat(t.refs()).isNotEmpty();
  }

  @Test
  void nestedStructureAccess() {
    var t = at("ВнутрФромВнешн = Внешн.Внутр", "ВнутрФромВнешн = ".length());
    assertThat(qnames(t)).contains("Структура");
  }

  @Test
  void valueTableStringColumnDoesNotCrash() {
    var t = at("ИмК = СтрокаТЗ.СтрКол", "ИмК = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void valueTableNumberColumn() {
    var t = at("К = СтрокаТЗ.ЧисКол", "К = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void valueTableDateColumn() {
    var t = at("Л = СтрокаТЗ.ДатКол", "Л = ".length());
    assertThat(qnames(t)).contains("Дата");
  }

  @Test
  void valueTableBooleanColumn() {
    var t = at("М = СтрокаТЗ.БулКол", "М = ".length());
    assertThat(qnames(t)).contains("Булево");
  }

  @Test
  void valueTableUntypedColumnIsUndefined() {
    var t = at("Н = СтрокаТЗ.БезТипа", "Н = ".length());
    assertThat(qnames(t)).contains("Неопределено");
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
      "./src/test/resources/types/AccumulatePatterns.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
