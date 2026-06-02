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
 * Покрывает дополнительные пути {@code inferIdentifier} / {@code inferUnary}
 * / {@code inferTernary} в
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}.
 */
@CleanupContextBeforeClassAndAfterClass
class GlobalScopeInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void classNameIdentifierIsNotInferredAsClassInstance() {
    // Структура без `Новый` — имя класса, не инстанс; inferIdentifier
    // отфильтровывает TYPE_NAME role.
    var types = at("КлассКакИдентификатор = Структура",
      "КлассКакИдентификатор = ".length());

    assertThat(types.refs())
      .as("Имя класса не должно резолвиться как тип-инстанс")
      .isEmpty();
  }

  @Test
  void undefinedIdentifierReturnsEmpty() {
    // когда reference не резолвится и GlobalScopeProvider тоже пуст.
    var types = at("ПустойРезолв = НесуществующийГлобал",
      "ПустойРезолв = ".length());

    assertThat(types.refs()).isEmpty();
  }

  @Test
  void doubleUnaryMinusIsNumber() {
    var types = at("ДвойнойМинус = --10", "ДвойнойМинус = ".length());

    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void globalFunctionAsBareIdentifierReturnsEmpty() {
    // Сообщить как identifier (не вызов) — PLATFORM_GLOBAL_METHOD filter
    // отсекает в inferIdentifier (L299).
    var types = at("ССылкаНаГлобал = Сообщить", "ССылкаНаГлобал = ".length());
    assertThat(types.refs()).isEmpty();
  }

  @Test
  void globalPropertyResolvesViaGlobalSymbolScope() {
    // КодировкаТекста — global property, должно резолвиться.
    var types = at("Кодировка = КодировкаТекста", "Кодировка = ".length());
    assertThat(types).isNotNull();
  }

  @Test
  void enumIdentifierResolvedViaScope() {
    var types = at("ЕнумПерем = НаправлениеПоиска", "ЕнумПерем = ".length());
    assertThat(types).isNotNull();
  }

  @Test
  void datePlusNumberIsDate() {
    var types = at("ДатаПлюсЧисло = '20200101' + 3600", "ДатаПлюсЧисло = ".length());
    assertThat(qnames(types)).containsExactly("Дата");
  }

  @Test
  void stringComparisonIsBoolean() {
    var types = at("СравнСтрок = \"abc\" = \"abc\"", "СравнСтрок = ".length());
    assertThat(qnames(types)).containsExactly("Булево");
  }

  @Test
  void nestedLogicalIsBoolean() {
    var types = at("СложноеЛог = (1 > 0) И (Истина ИЛИ Ложь)",
      "СложноеЛог = ".length());
    assertThat(qnames(types)).containsExactly("Булево");
  }

  @Test
  void ternaryReturnsUnionOfBranches() {
    // `?(Истина, "string", 42)` → union(Строка, Число).
    var types = at("Тернарный = ?(Истина, \"string\", 42)", "Тернарный = ".length());

    assertThat(qnames(types))
      .as("Тернарный возвращает union обеих веток")
      .containsExactlyInAnyOrder("Строка", "Число");
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
      "./src/test/resources/types/GlobalScopeInference.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
