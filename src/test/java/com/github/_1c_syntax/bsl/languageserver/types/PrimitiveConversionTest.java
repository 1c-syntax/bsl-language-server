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
 * Преобразование примитивов через глобальные функции Строка/Число/Булево/
 * Дата/Тип/ТипЗнч и форматирующие СтрШаблон/Формат/XMLСтрока.
 * Smoke-тесты — supplier не падает на любом из этих вызовов.
 */
@CleanupContextBeforeClassAndAfterClass
class PrimitiveConversionTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void stringConversionFromNumberDoesNotCrash() {
    var t = at("Стр1 = Строка(100)", "Стр1 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void stringConversionFromBooleanDoesNotCrash() {
    var t = at("Стр2 = Строка(Истина)", "Стр2 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void numberConversionFromStringDoesNotCrash() {
    var t = at("Чис1 = Число(\"123\")", "Чис1 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void booleanConversionFromNumberDoesNotCrash() {
    var t = at("Бул1 = Булево(0)", "Бул1 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void dateConversionFromComponentsDoesNotCrash() {
    var t = at("Дат1 = Дата(2020, 1, 1)", "Дат1 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void typeFunctionDoesNotCrash() {
    var t = at("Т1 = Тип(\"Число\")", "Т1 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void typeOfValueDoesNotCrash() {
    var t = at("ТЗ = ТипЗнч(100)", "ТЗ = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void formatDoesNotCrash() {
    var t = at("Форм = Формат(100, \"ЧЦ=10; ЧДЦ=2\")", "Форм = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void strTemplateDoesNotCrash() {
    var t = at("Шаблон = СтрШаблон(\"Значение: %1, ещё: %2\", \"первое\", 2)",
      "Шаблон = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void xmlStringDoesNotCrash() {
    var t = at("XmlЗнч = XMLСтрока(Истина)", "XmlЗнч = ".length());
    assertThat(t).isNotNull();
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
      "./src/test/resources/types/PrimitiveConversion.bsl");
  }
}
