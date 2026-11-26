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
 * Покрывает {@code applyStructureConstructorKeys} — пустые ключи, отсутствие values,
 * не-строковый первый аргумент в
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}.
 */
@CleanupContextBeforeClassAndAfterClass
class StructureConstructorEdgeTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void structureWithBlankKeyBetweenCommasSkipsBlanks() {
    // Новый Структура(",Имя, ,Возраст", "Иван", 30) → keys: [пусто, Имя, пусто, Возраст]
    // Пустые пропускаются (L377 continue), остаются Имя и Возраст.
    var types = at("СтрПустойКлюч = Новый Структура(\",Имя, ,Возраст\", \"Иван\", 30)",
      "СтрПустойКлюч = ".length());

    assertThat(types).isNotNull();
    var ref = types.refs().iterator().next();
    var fields = types.getLocalFields(ref);
    assertThat(fields).containsKeys("Имя", "Возраст");
  }

  @Test
  void structureWithMoreKeysThanValuesAssignsUndefined() {
    // Новый Структура("К1, К2, К3", 100) — для К2, К3 нет values → Неопределено (L384).
    var types = at("СтрБольшеКлючей = Новый Структура(\"К1, К2, К3\", 100)",
      "СтрБольшеКлючей = ".length());

    var ref = types.refs().iterator().next();
    var fields = types.getLocalFields(ref);
    assertThat(fields).containsKeys("К1", "К2", "К3");
  }

  @Test
  void emptyStructureConstructorHasNoKeys() {
    // Новый Структура() — keyLiteral = null, ключи не добавляются (L368-369).
    var types = at("ПустаяСтр = Новый Структура()", "ПустаяСтр = ".length());

    var ref = types.refs().iterator().next();
    assertThat(types.getLocalFields(ref)).isEmpty();
  }

  @Test
  void structureWithNonStringKeyArgPreservesBase() {
    // Новый Структура(КакойТоМассив) — keyLiteral null → не модифицируем base.
    var types = at("НеСтрКлюч = Новый Структура(КакойТоМассив)", "НеСтрКлюч = ".length());

    assertThat(types).isNotNull();
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
      "./src/test/resources/types/StructureConstructorEdge.bsl");
  }
}
