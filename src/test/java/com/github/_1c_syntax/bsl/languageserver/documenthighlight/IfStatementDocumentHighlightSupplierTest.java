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
