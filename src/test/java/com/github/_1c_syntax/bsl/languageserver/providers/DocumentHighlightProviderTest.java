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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentHighlightProviderTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight.bsl";

  @Autowired
  private DocumentHighlightProvider provider;

  @Test
  void testIfStatementHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на "Если" (строка 3, позиция 4)
    params.setPosition(new Position(3, 6));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Если, Тогда, ИначеЕсли, Тогда, Иначе, КонецЕсли
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);
  }

  @Test
  void testElseIfHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на "ИначеЕсли" (строка 5, позиция 4)
    params.setPosition(new Position(5, 6));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);
  }

  @Test
  void testForLoopHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на "Для" (строка 14, позиция 4)
    params.setPosition(new Position(14, 6));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, По, Цикл, КонецЦикла
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(4);
  }

  @Test
  void testWhileLoopHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на "Пока" (строка 18, позиция 4)
    params.setPosition(new Position(18, 6));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Пока, Цикл, КонецЦикла
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void testForEachLoopHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на "Для" в "Для Каждого" (строка 22, позиция 4)
    params.setPosition(new Position(22, 6));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, КонецЦикла
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(5);
  }

  @Test
  void testTryStatementHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на "Попытка" (строка 28, позиция 4)
    params.setPosition(new Position(28, 6));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Попытка, Исключение, КонецПопытки
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void testRegionHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на "#Область" (строка 35, позиция 2)
    params.setPosition(new Position(35, 3));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: #Область и #КонецОбласти
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void testNestedRegionHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на вложенную "#Область" (строка 37, позиция 4)
    params.setPosition(new Position(37, 6));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться только вложенные #Область и #КонецОбласти
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(2);
    assertThat(highlights).hasSizeLessThanOrEqualTo(4);
  }

  @Test
  void testParenthesesHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на открывающую скобку в "(1 + 2)" (строка 49, примерно позиция 24)
    params.setPosition(new Position(49, 24));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться обе скобки
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testSquareBracketsHighlight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на "[" в "Массив[0]" (строка 50, примерно позиция 18)
    params.setPosition(new Position(50, 18));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться обе квадратные скобки
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testNoHighlightOnNonKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // Кликаем на обычный идентификатор (строка 4, на "Сообщить")
    params.setPosition(new Position(4, 10));

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    // Может быть пусто или содержать только не-блочные элементы
    // В любом случае не должно падать
    assertThat(highlights).isNotNull();
  }
}
