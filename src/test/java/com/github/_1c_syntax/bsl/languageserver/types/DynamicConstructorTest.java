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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Покрывает {@code extractTypeName} / {@code stripQuotes} в
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}.
 */
@CleanupContextBeforeClassAndAfterClass
class DynamicConstructorTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void dynamicConstructorWithVariableTypeName() {
    // Новый (ИмяТипа) — имя типа лежит в переменной, статически оно неизвестно. Имя
    // переменной именем типа не является.
    var types = at("Объект1 = Новый (ИмяТипа)", "Объект1 = ".length());

    assertThat(types.refs()).isEmpty();
  }

  @Test
  void dynamicConstructorWithQuotedTypeName() {
    // Новый ("Структура") — имя названо строковым литералом, кавычки снимаются.
    var types = at("Объект2 = Новый (\"Структура\")", "Объект2 = ".length());

    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
  }

  @Test
  void dynamicConstructorWithConcatenatedTypeName() {
    // Имя собирается конкатенацией: у такого выражения узел представлен знаком операции,
    // и под именем «+» заводился несуществующий тип.
    var types = at("Объект3 = Новый (\"AddIn.\"", "Объект3 = ".length());

    assertThat(types.refs()).isEmpty();
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
      "./src/test/resources/types/DynamicConstructor.bsl");
  }
}
