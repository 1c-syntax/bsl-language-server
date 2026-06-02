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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inline (висячий) комментарий после присваивания: {@code X = F(); // Тип -}
 * должен типизировать переменную (сценарий из стандарта 1С:EDT для уточнения
 * типов локальных переменных).
 */
@CleanupContextBeforeClassAndAfterClass
class InlineTypeCommentInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void singleTypeWithTrailingDash() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/InlineTypeComment.bsl");

    var types = inferAtMarker(documentContext, "X = Значение", "X = ".length());
    assertThat(types.refs())
      .as("inline `// Число -` produces Число")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Число");
  }

  @Test
  void singleTypeWithoutDash() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/InlineTypeComment.bsl");

    var types = inferAtMarker(documentContext, "Y = Имя", "Y = ".length());
    assertThat(types.refs())
      .as("inline `// Строка` (no dash) also produces Строка")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");
  }

  @Test
  void unionOfTypesSeparatedByComma() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/InlineTypeComment.bsl");

    var types = inferAtMarker(documentContext, "Z = Перечисление", "Z = ".length());
    assertThat(types.refs())
      .as("inline `// Число, Строка -` produces union")
      .extracting(ref -> ref.qualifiedName())
      .containsExactlyInAnyOrder("Число", "Строка");
  }

  private com.github._1c_syntax.bsl.languageserver.types.model.TypeSet inferAtMarker(
    com.github._1c_syntax.bsl.languageserver.context.DocumentContext documentContext,
    String marker,
    int offsetInMarker
  ) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine + 1));
  }
}
