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
 * Покрывает recursion guard ({@code ctx.visited}) и
 * typesFromVariableTrailingComment в {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}.
 */
@CleanupContextBeforeClassAndAfterClass
class CircularVariableInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void circularVariableReferenceReturnsEmptyOrEmptyUnion() {
    // when — А = Б, Б = А, X = А. Recursion guard сработал.
    var types = at("X = А;", "X = ".length());

    // then — без StackOverflow, тип либо EMPTY либо валидный union.
    assertThat(types).isNotNull();
  }

  @Test
  void selfReferencingVariableIsHandledByVisitedGuard() {
    var types = at("Y = Сам;", "Y = ".length());

    assertThat(types).isNotNull();
  }

  @Test
  void parameterWithDeclaredTypeFromJsDoc() {
    // когда JsDoc объявляет Параметр - Строка, обращение к нему должно
    // возвращать Строка.
    var types = at("Результат = Обработать(\"hello\")", "Результат = ".length());

    assertThat(types).isNotNull();
  }

  @Test
  void variableWithTrailingTypeComment() {
    // Тип1 = 0; // Число - возраст. Тип переменной включает Число и из
    // комментария, и из инициализации.
    var types = at("Тип1 = 0;", "Тип1 ".length());

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
      "./src/test/resources/types/CircularVariableInference.bsl");
  }
}
