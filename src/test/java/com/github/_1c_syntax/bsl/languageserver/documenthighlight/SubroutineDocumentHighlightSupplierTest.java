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
class SubroutineDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/SubroutineDocumentHighlight.bsl";

  @Autowired
  private SubroutineDocumentHighlightSupplier supplier;

  @Test
  void testProcedureKeyword() {
    // given
    // Строка 2 (0-based): "Процедура ТестПроцедура()"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(2, 3);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Процедура"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Процедура и КонецПроцедуры
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 2, 0, 2, 9);      // Процедура
    assertHighlightRange(highlights, 4, 0, 4, 14);     // КонецПроцедуры
  }

  @Test
  void testEndProcedureKeyword() {
    // given
    // Строка 4 (0-based): "КонецПроцедуры"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(4, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "КонецПроцедуры"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 2, 0, 2, 9);      // Процедура
    assertHighlightRange(highlights, 4, 0, 4, 14);     // КонецПроцедуры
  }

  @Test
  void testFunctionKeyword() {
    // given
    // Строка 6 (0-based): "Функция ТестФункция()"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(6, 3);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Функция"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Функция и КонецФункции
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 6, 0, 6, 7);      // Функция
    assertHighlightRange(highlights, 8, 0, 8, 12);     // КонецФункции
  }

  @Test
  void testEndFunctionKeyword() {
    // given
    // Строка 8 (0-based): "КонецФункции"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(8, 5);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "КонецФункции"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 6, 0, 6, 7);      // Функция
    assertHighlightRange(highlights, 8, 0, 8, 12);     // КонецФункции
  }

  @Test
  void testNonSubroutineKeyword() {
    // given
    // Строка 16 (0-based): "    Если Истина Тогда"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(16, 4);
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext); // На "Если" (не процедура)

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
