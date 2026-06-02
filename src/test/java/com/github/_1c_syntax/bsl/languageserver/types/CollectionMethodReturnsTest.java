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
 * Возвращаемые типы методов коллекций (Массив/Структура/Соответствие/ТЗ/
 * СписокЗначений) — проверка что supplier работает на каждом методе.
 */
@CleanupContextBeforeClassAndAfterClass
class CollectionMethodReturnsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void arrayCountReturnsNumber() {
    var t = at("Кол = М.Количество()", "Кол = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void arrayUpperBoundReturnsNumber() {
    var t = at("Граница = М.ВГраница()", "Граница = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void arrayContainsDoesNotCrash() {
    var t = at("Содерж = М.Содержит(2)", "Содерж = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void arrayCopyReturnsArray() {
    var t = at("Копия = М.Скопировать()", "Копия = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void structureCountReturnsNumber() {
    var t = at("КолС = С.Количество()", "КолС = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void structurePropertyReturnsBoolean() {
    var t = at("Свой = С.Свойство(\"X\")", "Свой = ".length());
    assertThat(qnames(t)).contains("Булево");
  }

  @Test
  void mapCountReturnsNumber() {
    var t = at("КолКарт = Карта.Количество()", "КолКарт = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void valueTableCountReturnsNumber() {
    var t = at("КолТЗ = ТЗ.Количество()", "КолТЗ = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void valueTableColumnsCount() {
    var t = at("КолКол = ТЗ.Колонки.Количество()", "КолКол = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void valueListCountReturnsNumber() {
    var t = at("КолСЗ = СЗ.Количество()", "КолСЗ = ".length());
    assertThat(qnames(t)).contains("Число");
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
      "./src/test/resources/types/CollectionMethodReturns.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
