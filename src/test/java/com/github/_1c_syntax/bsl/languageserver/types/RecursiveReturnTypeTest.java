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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Функция, кладущая свой же результат в поле возвращаемой структуры, задаёт значением
 * уравнение {@code T = Структура{Вложенный: T}}.
 */
@CleanupContextBeforeClassAndAfterClass
class RecursiveReturnTypeTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void recursiveFunctionKeepsItsOwnTypeInField() {
    // given
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/RecursiveReturn.bsl");

    // when: тип значения вызова рекурсивной функции.
    var types = typeService.expressionTypesAt(documentContext,
      positionOf(documentContext, "УзелДерева(Неопределено)"));

    // then: у него оба поля, и поле, заполненное вызовом самой функции, снова несёт её тип —
    // а не пустоту, как если бы рекурсивное ребро просто обрубили.
    var ref = types.refs().iterator().next();
    assertThat(types.getAllFieldNames()).contains("Имя", "Вложенный");
    var nested = types.getLocalFields(ref).get("Вложенный").types();
    assertThat(nested.getAllFieldNames())
      .as("поле, заполненное рекурсивным вызовом, несёт тот же тип")
      .contains("Имя", "Вложенный");
  }

  @Test
  void arrayDeclaredAsCollectionOfItselfCarriesItsOwnType() {
    // given: функция объявлена возвращающей массив своих же результатов —
    // `Массив из см. ЭтаЖеФункция`. Уравнение то же, что у структуры с полем, только
    // ребро идёт через элемент коллекции.
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/RecursiveCollection.bsl");

    // when
    var types = typeService.expressionTypesAt(documentContext,
      positionOf(documentContext, "Ветки();"));

    // then: элемент массива — снова массив, то есть ребро не обрублено.
    var ref = types.refs().iterator().next();
    assertThat(types.getElementTypes(ref).refs())
      .as("элемент массива, объявленного через себя, несёт тот же тип")
      .containsExactly(ref);
  }

  @Test
  void navigationThroughSelfTypedArrayResolves() {
    // given
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/RecursiveCollection.bsl");

    // when: два уровня индексации по массиву, объявленному через себя.
    var types = typeService.expressionTypesAt(documentContext,
      positionOf(documentContext, "Ветки()[0][0]"));

    // then: разворачивается по уровню на обращение и не уходит в бесконечность.
    assertThat(types.refs())
      .as("обращение по индексу к массиву из самого себя снова даёт массив")
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Массив");
  }

  /** Позиция первого вхождения текста в документе — на его первом символе. */
  private static Position positionOf(DocumentContext documentContext, String text) {
    var content = documentContext.getContent();
    var index = content.indexOf(text);
    var line = content.substring(0, index).split("\n").length - 1;
    var lineStart = content.lastIndexOf('\n', index) + 1;
    return new Position(line, index - lineStart + 1);
  }
}
