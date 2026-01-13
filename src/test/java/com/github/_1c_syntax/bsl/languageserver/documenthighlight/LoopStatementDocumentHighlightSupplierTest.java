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
package com.github._1c_syntax.bsl.languageserver.documenthighlight;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoopStatementDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight.bsl";

  @Autowired
  private LoopStatementDocumentHighlightSupplier supplier;

  @Test
  void testForLoop() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(13, 4)); // На "Для" (строка 14 в 1-based, 13 в 0-based)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, По, Цикл, КонецЦикла
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(4);
  }

  @Test
  void testWhileLoop() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(17, 4)); // На "Пока" (строка 18 в 1-based, 17 в 0-based)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Пока, Цикл, КонецЦикла
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void testForEachLoop() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(21, 4)); // На "Для" в "Для Каждого" (строка 22 в 1-based, 21 в 0-based)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, КонецЦикла
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(5);
  }

  @Test
  void testDoKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(13, 27)); // На "Цикл" (строка 14, после "Для Счетчик = 1 По 10 ")

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(4);
  }

  @Test
  void testNonLoopKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, 4)); // На "Если" (не цикл)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isEmpty();
  }

  @Test
  void testNestedForEachInsideWhile() {
    // given
    // Тестируем вложенный цикл "Для Каждого" внутри "Пока"
    // Строка 28 (0-based) содержит "Для Каждого Элемент Из Массив Цикл" внутри "Пока"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(28, 8)); // На "Для" во вложенном цикле

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, КонецЦикла внутреннего цикла
    // НЕ должны подсветиться ключевые слова внешнего цикла "Пока"
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(5);

    // Проверяем, что все подсвеченные элементы находятся на строках 28-30 (внутренний цикл)
    // а не на строках 27, 31 (внешний цикл "Пока")
    for (var highlight : highlights) {
      var line = highlight.getRange().getStart().getLine();
      assertThat(line).isGreaterThanOrEqualTo(28);
      assertThat(line).isLessThanOrEqualTo(30);
    }
  }
}
