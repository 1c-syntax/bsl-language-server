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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #4179: переменной, которой присвоен результат функции с объявленным типом
 * возвращаемого значения {@code Массив из Число}, не должен «подмешиваться»
 * платформенный дефолтный тип элемента ({@code Произвольный}).
 */
@CleanupContextBeforeClassAndAfterClass
class CollectionReturnElementTypeInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Test
  void declaredArrayElementTypeIsNotPollutedByDefault() {
    var types = inferVar("Числа");
    var arrayRef = types.refs().iterator().next();

    assertThat(arrayRef.qualifiedName()).isEqualTo("Массив");
    assertThat(types.getElementTypes(arrayRef).refs())
      .as("объявленный `Массив из Число` не должен получать платформенный дефолт элемента")
      .extracting(r -> r.qualifiedName())
      .containsExactly("Число");
  }

  @Test
  void wrapperCollectionDefaultElementTypeIsPreserved() {
    // Без явной "из"-аннотации платформенный дефолтный тип элемента обёрточной
    // коллекции (ЭлементСпискаЗначений для СписокЗначений) должен сохраняться —
    // на нём держится вывод типа элемента в `Для Каждого`.
    var types = inferVar("Список");
    var listRef = types.refs().iterator().next();

    assertThat(listRef.qualifiedName()).isEqualTo("СписокЗначений");
    assertThat(types.getElementTypes(listRef).refs())
      .as("дефолтный тип элемента обёрточной коллекции сохраняется")
      .extracting(r -> r.qualifiedName())
      .contains("ЭлементСпискаЗначений");
  }

  private TypeSet inferVar(String varName) {
    var dc = doc();
    var method = dc.getSymbolTree().getMethods().stream()
      .filter(m -> m.getName().equalsIgnoreCase("Пример"))
      .findFirst()
      .orElseThrow();
    VariableSymbol variable = dc.getSymbolTree()
      .getVariableSymbol(varName, method)
      .orElseThrow();
    return inferencer.inferSymbol(variable);
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/CollectionReturnElementType.bsl");
  }
}
