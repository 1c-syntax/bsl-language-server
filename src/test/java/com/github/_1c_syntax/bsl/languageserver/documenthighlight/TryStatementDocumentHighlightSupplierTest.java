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
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TryStatementDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/TryStatementDocumentHighlight.bsl";

  @Autowired
  private TryStatementDocumentHighlightSupplier supplier;

  @Test
  void testTryKeyword() {
    // given
    // Строка 3 (0-based): "    Попытка"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(3, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Попытка"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Попытка, Исключение, КонецПопытки
    assertThat(highlights).hasSize(3);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 11);     // Попытка
    assertHighlightRange(highlights, 5, 4, 5, 14);     // Исключение
    assertHighlightRange(highlights, 7, 4, 7, 16);     // КонецПопытки
  }

  @Test
  void testExceptKeyword() {
    // given
    // Строка 5 (0-based): "    Исключение"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(5, 6);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Исключение"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(3);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 11);     // Попытка
    assertHighlightRange(highlights, 5, 4, 5, 14);     // Исключение
    assertHighlightRange(highlights, 7, 4, 7, 16);     // КонецПопытки
  }

  @Test
  void testEndTryKeyword() {
    // given
    // Строка 7 (0-based): "    КонецПопытки;"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(7, 6);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "КонецПопытки"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(3);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 11);     // Попытка
    assertHighlightRange(highlights, 5, 4, 5, 14);     // Исключение
    assertHighlightRange(highlights, 7, 4, 7, 16);     // КонецПопытки
  }

  @Test
  void testNonTryKeyword() {
    // given
    // Строка 11 (0-based): "    Если Истина Тогда"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(11, 4);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если" (не try)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isEmpty();
  }

  @Test
  void testNestedTryOuter() {
    // given
    // Тест вложенных try-конструкций - внешняя попытка
    // Строка 18 (0-based): "    Попытка" (внешняя)
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(18, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Попытка" (внешняя)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться только ключевые слова внешней попытки: Попытка, Исключение, КонецПопытки
    assertThat(highlights).hasSize(3);

    assertHighlightRange(highlights, 18, 4, 18, 11);    // Попытка (внешняя)
    assertHighlightRange(highlights, 24, 4, 24, 14);    // Исключение (внешняя)
    assertHighlightRange(highlights, 26, 4, 26, 16);    // КонецПопытки (внешняя)
  }

  @Test
  void testNestedTryInner() {
    // given
    // Строка 19 (0-based): "        Попытка" (внутренняя)
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(19, 9);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Попытка" (внутренняя)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться только ключевые слова внутренней попытки: Попытка, Исключение, КонецПопытки
    assertThat(highlights).hasSize(3);

    assertHighlightRange(highlights, 19, 8, 19, 15);    // Попытка (внутренняя)
    assertHighlightRange(highlights, 21, 8, 21, 18);    // Исключение (внутренняя)
    assertHighlightRange(highlights, 23, 8, 23, 20);    // КонецПопытки (внутренняя)
  }

  @Test
  void testTryInsideIf() {
    // given
    // Тест попытки внутри if
    // Строка 32 (0-based): "        Попытка"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(32, 9);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Попытка"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Попытка, Исключение, КонецПопытки
    assertThat(highlights).hasSize(3);

    assertHighlightRange(highlights, 32, 8, 32, 15);    // Попытка
    assertHighlightRange(highlights, 34, 8, 34, 18);    // Исключение
    assertHighlightRange(highlights, 36, 8, 36, 20);    // КонецПопытки
  }

  private void assertHighlightRange(List<DocumentHighlight> highlights,
                                     int startLine, int startChar,
                                     int endLine, int endChar) {
    var expectedRange = new Range(
      new Position(startLine, startChar),
      new Position(endLine, endChar)
    );
    assertThat(highlights)
      .extracting(DocumentHighlight::getRange)
      .contains(expectedRange);
  }
}
