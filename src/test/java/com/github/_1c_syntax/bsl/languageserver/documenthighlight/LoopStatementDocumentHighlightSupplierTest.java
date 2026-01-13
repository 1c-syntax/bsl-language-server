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

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/LoopStatementDocumentHighlight.bsl";

  @Autowired
  private LoopStatementDocumentHighlightSupplier supplier;

  @Test
  void testForLoop() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, 4)); // На "Для"

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
    params.setPosition(new Position(7, 4)); // На "Пока"

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
    params.setPosition(new Position(11, 4)); // На "Для" в "Для Каждого"

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
    params.setPosition(new Position(3, 27)); // На "Цикл"

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
    params.setPosition(new Position(34, 4)); // На "Если" (не цикл)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isEmpty();
  }

  @Test
  void testNestedForEachInsideWhile() {
    // given
    // Тестируем вложенный цикл "Для Каждого" внутри "Пока"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(18, 8)); // На "Для" во вложенном цикле

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, КонецЦикла внутреннего цикла
    // НЕ должны подсветиться ключевые слова внешнего цикла "Пока"
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(5);

    // Проверяем, что все подсвеченные элементы находятся на строках 18-20 (внутренний цикл)
    for (var highlight : highlights) {
      var line = highlight.getRange().getStart().getLine();
      assertThat(line).isGreaterThanOrEqualTo(18);
      assertThat(line).isLessThanOrEqualTo(20);
    }
  }

  @Test
  void testBreakKeywordHighlightsLoop() {
    // given
    // Строка 8 содержит "Прервать;" внутри цикла "Пока"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(8, 8)); // На "Прервать"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Пока, Цикл, КонецЦикла, Прервать
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(4);
  }

  @Test
  void testContinueKeywordHighlightsLoop() {
    // given
    // Строка 12 содержит "Продолжить;" внутри цикла "Для Каждого"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(12, 8)); // На "Продолжить"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, КонецЦикла, Продолжить
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);
  }

  @Test
  void testWhileLoopIncludesBreak() {
    // given
    // При клике на "Пока" должен подсвечиваться и "Прервать" внутри цикла
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(7, 4)); // На "Пока"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Пока, Цикл, КонецЦикла, Прервать
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(4);

    // Проверяем, что "Прервать" на строке 8 тоже подсвечен
    var breakHighlighted = highlights.stream()
      .anyMatch(h -> h.getRange().getStart().getLine() == 8);
    assertThat(breakHighlighted).isTrue();
  }

  @Test
  void testBreakInsideIfInsideLoop() {
    // given
    // Строка 27 содержит "Прервать;" внутри "Если" внутри цикла "Для Каждого"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(27, 12)); // На "Прервать"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, КонецЦикла, Прервать
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);
  }

  @Test
  void testForEachLoopIncludesBreakInsideIf() {
    // given
    // При клике на "Для" должен подсвечиваться и "Прервать" внутри if внутри цикла
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(25, 4)); // На "Для"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, КонецЦикла, Прервать
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);

    // Проверяем, что "Прервать" на строке 27 тоже подсвечен
    var breakHighlighted = highlights.stream()
      .anyMatch(h -> h.getRange().getStart().getLine() == 27);
    assertThat(breakHighlighted).isTrue();
  }
}
