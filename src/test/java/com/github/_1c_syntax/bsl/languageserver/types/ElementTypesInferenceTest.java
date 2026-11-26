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
 * Element types для коллекций (Массив из X, Соответствие из X) должны
 * читаться из JsDoc и попадать в TypeSet.elementTypes.
 */
@CleanupContextBeforeClassAndAfterClass
class ElementTypesInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void arrayOfSingleType() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ArrayElementTypes.bsl");

    var types = inferAtMarker(documentContext, "СписокЧисел = Числа()", "СписокЧисел = ".length());
    assertThat(types.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Массив");
    var arrayRef = types.refs().iterator().next();
    assertThat(types.getElementTypes(arrayRef).refs())
      .as("Массив из Число → element type = Число")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Число");
  }

  @Test
  void arrayOfDifferentSingleType() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ArrayElementTypes.bsl");

    var types = inferAtMarker(documentContext, "СписокСтрок = Строки()", "СписокСтрок = ".length());
    assertThat(types.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Массив");
    var arrayRef = types.refs().iterator().next();
    assertThat(types.getElementTypes(arrayRef).refs())
      .as("Массив из Строка → element type = Строка")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");
  }

  @Test
  void arrayOfMultipleValueTypes() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ArrayElementTypes.bsl");

    var types = inferAtMarker(documentContext, "СмешанныйСписок = ЧислаИлиСтроки()", "СмешанныйСписок = ".length());
    assertThat(types.refs())
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Массив");
    var arrayRef = types.refs().iterator().next();
    assertThat(types.getElementTypes(arrayRef).refs())
      .as("Массив из Число, Строка → element types = {Число, Строка}")
      .extracting(ref -> ref.qualifiedName())
      .containsExactlyInAnyOrder("Число", "Строка");
  }

  private TypeSet inferAtMarker(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine + 1));
  }
}
