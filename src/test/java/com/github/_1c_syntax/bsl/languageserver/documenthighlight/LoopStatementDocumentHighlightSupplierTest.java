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
class LoopStatementDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/LoopStatementDocumentHighlight.bsl";

  @Autowired
  private LoopStatementDocumentHighlightSupplier supplier;

  @Test
  void testForLoop() {
    // given
    // Строка 3 (0-based): "    Для Счетчик = 1 По 10 Цикл"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(3, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Для"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, По, Цикл, КонецЦикла
    assertThat(highlights).hasSize(4);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 7);      // Для
    assertHighlightRange(highlights, 3, 20, 3, 22);    // По
    assertHighlightRange(highlights, 3, 26, 3, 30);    // Цикл
    assertHighlightRange(highlights, 5, 4, 5, 14);     // КонецЦикла
  }

  @Test
  void testWhileLoop() {
    // given
    // Строка 7 (0-based): "    Пока Истина Цикл"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(7, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Пока"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Пока, Цикл, Прервать, КонецЦикла
    assertThat(highlights).hasSize(4);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 7, 4, 7, 8);      // Пока
    assertHighlightRange(highlights, 7, 16, 7, 20);    // Цикл
    assertHighlightRange(highlights, 8, 8, 8, 16);     // Прервать
    assertHighlightRange(highlights, 9, 4, 9, 14);     // КонецЦикла
  }

  @Test
  void testForEachLoop() {
    // given
    // Строка 11 (0-based): "    Для Каждого Элемент Из Массив Цикл"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(11, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Для" в "Для Каждого"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, Продолжить, КонецЦикла
    assertThat(highlights).hasSize(6);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 11, 4, 11, 7);     // Для
    assertHighlightRange(highlights, 11, 8, 11, 15);    // Каждого
    assertHighlightRange(highlights, 11, 24, 11, 26);   // Из
    assertHighlightRange(highlights, 11, 34, 11, 38);   // Цикл
    assertHighlightRange(highlights, 12, 8, 12, 18);    // Продолжить
    assertHighlightRange(highlights, 13, 4, 13, 14);    // КонецЦикла
  }

  @Test
  void testDoKeyword() {
    // given
    // Строка 3 (0-based): "    Для Счетчик = 1 По 10 Цикл"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(3, 27);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Цикл"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(4);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 7);      // Для
    assertHighlightRange(highlights, 3, 26, 3, 30);    // Цикл
    assertHighlightRange(highlights, 5, 4, 5, 14);     // КонецЦикла
  }

  @Test
  void testNonLoopKeyword() {
    // given
    // Строка 34 (0-based): "    Если Истина Тогда"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(34, 4);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если" (не цикл)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isEmpty();
  }

  @Test
  void testNestedForEachInsideWhile() {
    // given
    // Строка 18 (0-based): "        Для Каждого Элемент Из Массив Цикл"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(18, 9);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Для" во вложенном цикле

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, Продолжить, КонецЦикла внутреннего цикла
    assertThat(highlights).hasSize(6);

    // Проверяем точные позиции токенов внутреннего цикла
    assertHighlightRange(highlights, 18, 8, 18, 11);   // Для
    assertHighlightRange(highlights, 18, 12, 18, 19);  // Каждого
    assertHighlightRange(highlights, 18, 28, 18, 30);  // Из
    assertHighlightRange(highlights, 18, 38, 18, 42);  // Цикл
    assertHighlightRange(highlights, 19, 12, 19, 22);  // Продолжить
    assertHighlightRange(highlights, 20, 8, 20, 18);   // КонецЦикла
  }

  @Test
  void testBreakKeywordHighlightsLoop() {
    // given
    // Строка 8 (0-based): "        Прервать;"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(8, 10);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Прервать"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Пока, Цикл, Прервать, КонецЦикла
    assertThat(highlights).hasSize(4);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 7, 4, 7, 8);      // Пока
    assertHighlightRange(highlights, 8, 8, 8, 16);     // Прервать
    assertHighlightRange(highlights, 9, 4, 9, 14);     // КонецЦикла
  }

  @Test
  void testContinueKeywordHighlightsLoop() {
    // given
    // Строка 12 (0-based): "        Продолжить;"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(12, 10);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Продолжить"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, Продолжить, КонецЦикла
    assertThat(highlights).hasSize(6);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 11, 4, 11, 7);    // Для
    assertHighlightRange(highlights, 12, 8, 12, 18);   // Продолжить
    assertHighlightRange(highlights, 13, 4, 13, 14);   // КонецЦикла
  }

  @Test
  void testWhileLoopIncludesBreak() {
    // given
    // Строка 7 (0-based): "    Пока Истина Цикл"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(7, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Пока"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(4);

    // Проверяем, что "Прервать" на строке 8 тоже подсвечен
    assertHighlightRange(highlights, 8, 8, 8, 16);   // Прервать
  }

  @Test
  void testBreakInsideIfInsideLoop() {
    // given
    // Строка 27 (0-based): "            Прервать;"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(27, 13);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Прервать"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Для, Каждого, Из, Цикл, Прервать, КонецЦикла
    assertThat(highlights).hasSize(6);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 25, 4, 25, 7);    // Для
    assertHighlightRange(highlights, 25, 8, 25, 15);   // Каждого
    assertHighlightRange(highlights, 25, 24, 25, 26);  // Из
    assertHighlightRange(highlights, 25, 34, 25, 38);  // Цикл
    assertHighlightRange(highlights, 27, 12, 27, 20);  // Прервать
    assertHighlightRange(highlights, 29, 4, 29, 14);   // КонецЦикла
  }

  @Test
  void testForEachLoopIncludesBreakInsideIf() {
    // given
    // Строка 25 (0-based): "    Для Каждого Элемент Из Массив Цикл"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(25, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Для"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(6);

    // Проверяем, что "Прервать" на строке 27 тоже подсвечен с точной позицией
    assertHighlightRange(highlights, 27, 12, 27, 20);  // Прервать
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
