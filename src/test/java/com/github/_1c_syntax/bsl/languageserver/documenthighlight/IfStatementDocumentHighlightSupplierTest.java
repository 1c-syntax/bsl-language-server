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
class IfStatementDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/IfStatementDocumentHighlight.bsl";

  @Autowired
  private IfStatementDocumentHighlightSupplier supplier;

  @Test
  void testIfKeyword() {
    // given
    // Строка 3 (0-based): "    Если Истина Тогда"
    // "Если" на позиции 4-7, "Тогда" на позиции 16-20
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(3, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Если, Тогда, ИначеЕсли, Тогда, Иначе, КонецЕсли
    assertThat(highlights).hasSize(6);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 8);      // Если
    assertHighlightRange(highlights, 3, 16, 3, 21);    // Тогда
    assertHighlightRange(highlights, 5, 4, 5, 13);     // ИначеЕсли
    assertHighlightRange(highlights, 5, 19, 5, 24);    // Тогда
    assertHighlightRange(highlights, 7, 4, 7, 9);      // Иначе
    assertHighlightRange(highlights, 9, 4, 9, 13);     // КонецЕсли
  }

  @Test
  void testElseIfKeyword() {
    // given
    // Строка 5 (0-based): "    ИначеЕсли Ложь Тогда"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(5, 6);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "ИначеЕсли"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(6);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 8);      // Если
    assertHighlightRange(highlights, 5, 4, 5, 13);     // ИначеЕсли
    assertHighlightRange(highlights, 9, 4, 9, 13);     // КонецЕсли
  }

  @Test
  void testElseKeyword() {
    // given
    // Строка 7 (0-based): "    Иначе"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(7, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Иначе"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(6);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 8);      // Если
    assertHighlightRange(highlights, 7, 4, 7, 9);      // Иначе
    assertHighlightRange(highlights, 9, 4, 9, 13);     // КонецЕсли
  }

  @Test
  void testEndIfKeyword() {
    // given
    // Строка 9 (0-based): "    КонецЕсли;"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(9, 6);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "КонецЕсли"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(6);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 3, 4, 3, 8);      // Если
    assertHighlightRange(highlights, 9, 4, 9, 13);     // КонецЕсли
  }

  @Test
  void testNonIfKeyword() {
    // given
    // Строка 13 (0-based): "    Пока Истина Цикл"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(13, 4);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Пока" (не if)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isEmpty();
  }

  @Test
  void testNestedIfStatement() {
    // given
    // Тест вложенных if-конструкций
    // Строка 20 (0-based): "    Если Истина Тогда" (внешний if)
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(20, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если" (внешний)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться только ключевые слова внешнего if: Если, Тогда, КонецЕсли
    assertThat(highlights).hasSize(3);

    assertHighlightRange(highlights, 20, 4, 20, 8);     // Если (внешний)
    assertHighlightRange(highlights, 20, 16, 20, 21);   // Тогда (внешний)
    assertHighlightRange(highlights, 24, 4, 24, 13);    // КонецЕсли (внешний)
  }

  @Test
  void testNestedInnerIfStatement() {
    // given
    // Строка 21 (0-based): "        Если Ложь Тогда" (внутренний if)
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(21, 9);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если" (внутренний)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться только ключевые слова внутреннего if: Если, Тогда, КонецЕсли
    assertThat(highlights).hasSize(3);

    assertHighlightRange(highlights, 21, 8, 21, 12);    // Если (внутренний)
    assertHighlightRange(highlights, 21, 18, 21, 23);   // Тогда (внутренний)
    assertHighlightRange(highlights, 23, 8, 23, 17);    // КонецЕсли (внутренний)
  }

  @Test
  void testMultipleElseIf() {
    // given
    // Тест множественных ИначеЕсли
    // Строка 29 (0-based): "    Если Ложь Тогда"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(29, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Если, Тогда, 3xИначеЕсли, 3xТогда, Иначе, КонецЕсли = 10
    assertThat(highlights).hasSize(10);

    assertHighlightRange(highlights, 29, 4, 29, 8);     // Если
    assertHighlightRange(highlights, 29, 14, 29, 19);   // Тогда
    assertHighlightRange(highlights, 31, 4, 31, 13);    // ИначеЕсли 1
    assertHighlightRange(highlights, 33, 4, 33, 13);    // ИначеЕсли 2
    assertHighlightRange(highlights, 35, 4, 35, 13);    // ИначеЕсли 3
    assertHighlightRange(highlights, 37, 4, 37, 9);     // Иначе
    assertHighlightRange(highlights, 39, 4, 39, 13);    // КонецЕсли
  }

  @Test
  void testSimpleIfWithoutElse() {
    // given
    // Тест if без ИначеЕсли и Иначе
    // Строка 44 (0-based): "    Если Истина Тогда"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(44, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Если, Тогда, КонецЕсли
    assertThat(highlights).hasSize(3);

    assertHighlightRange(highlights, 44, 4, 44, 8);     // Если
    assertHighlightRange(highlights, 44, 16, 44, 21);   // Тогда
    assertHighlightRange(highlights, 46, 4, 46, 13);    // КонецЕсли
  }

  @Test
  void testIfWithElseIfButNoElse() {
    // given
    // Тест if без Иначе, но с ИначеЕсли
    // Строка 51 (0-based): "    Если Ложь Тогда"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(51, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Если, Тогда, ИначеЕсли, Тогда, КонецЕсли
    assertThat(highlights).hasSize(5);

    assertHighlightRange(highlights, 51, 4, 51, 8);     // Если
    assertHighlightRange(highlights, 51, 14, 51, 19);   // Тогда
    assertHighlightRange(highlights, 53, 4, 53, 13);    // ИначеЕсли
    assertHighlightRange(highlights, 53, 21, 53, 26);   // Тогда
    assertHighlightRange(highlights, 55, 4, 55, 13);    // КонецЕсли
  }

  @Test
  void testThenKeyword() {
    // given
    // Клик на "Тогда" должен подсветить весь блок if
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(3, 18); // На "Тогда"
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext);

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(6);

    assertHighlightRange(highlights, 3, 4, 3, 8);      // Если
    assertHighlightRange(highlights, 3, 16, 3, 21);    // Тогда
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
