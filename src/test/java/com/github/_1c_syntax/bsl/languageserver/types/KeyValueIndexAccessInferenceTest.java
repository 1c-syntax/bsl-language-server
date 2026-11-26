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
 * Тип значения по ключу в KV-коллекциях (Структура / Соответствие) — точный
 * по строковому литералу, union по динамическому индексу, empty для
 * неизвестных ключей. Сценарии параллельны completion-тестам, но проверяют
 * результат {@link TypeService#expressionTypesAt} напрямую — на уровне TypeSet,
 * без учёта как именно потом этот тип отобразится в выпадашке.
 */
@CleanupContextBeforeClassAndAfterClass
class KeyValueIndexAccessInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void structureIndexAccessWithStringLiteralKeyReturnsValueType() {
    var content = """
      Стр = Новый Структура;
      Стр.Вставить("X", 42);
      Y = Стр["X"];
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "Y = Стр[\"X\"]", "Y = ".length());

    assertThat(refNames(types))
      .as("Стр[\"X\"] — значение Число")
      .contains("Число");
  }

  @Test
  void mapIndexAccessWithStringLiteralKeyReturnsValueType() {
    var content = """
      С = Новый Соответствие;
      С.Вставить("X", "значение");
      Y = С["X"];
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "Y = С[\"X\"]", "Y = ".length());

    assertThat(refNames(types))
      .as("Соответствие[\"X\"] — значение Строка")
      .contains("Строка");
  }

  @Test
  void structureIndexAccessWithUnknownKeyReturnsEmpty() {
    var content = """
      Стр = Новый Структура;
      Стр.Вставить("X", 42);
      Y = Стр["Other"];
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "Y = Стр[\"Other\"]", "Y = ".length());

    assertThat(types.refs())
      .as("Стр[\"Other\"] — ключа нет, тип неизвестен")
      .isEmpty();
  }

  @Test
  void structureIndexAccessWithDynamicKeyUnionsValueTypes() {
    var content = """
      Стр = Новый Структура;
      Стр.Вставить("X", 42);
      Стр.Вставить("Y", "значение");
      Ключ = "X";
      Z = Стр[Ключ];
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "Z = Стр[Ключ]", "Z = ".length());

    assertThat(refNames(types))
      .as("динамический индекс — union value-типов (Число + Строка)")
      .contains("Число", "Строка");
  }

  @Test
  void structureIndexAccessDoesNotReturnKeyAndValueWrapper() {
    // Регрессия: до фикса #14 возвращался дефолт коллекции — КлючИЗначение,
    // т.е. совершенно не то, что лежит по ключу.
    var content = """
      Стр = Новый Структура;
      Стр.Вставить("X", 42);
      Y = Стр["X"];
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var types = inferAtMarker(documentContext, "Y = Стр[\"X\"]", "Y = ".length());

    assertThat(refNames(types))
      .as("Стр[\"X\"] — значение по ключу, не КлючИЗначение")
      .doesNotContain("КлючИЗначение");
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

  private static List<String> refNames(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
