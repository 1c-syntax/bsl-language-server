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
class BracketDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/BracketDocumentHighlight.bsl";

  @Autowired
  private BracketDocumentHighlightSupplier supplier;

  @Test
  void testOpenParenthesis() {
    // given
    // Строка 4 (0-based): "    Массив.Добавить((1 + 2) * 3);"
    // Первая "(" на позиции 19, вторая на позиции 20
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(4, 20); // На вложенную "(" в "(1 + 2)"
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext);

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции вложенных скобок
    assertHighlightRange(highlights, 4, 20, 4, 21);  // (
    assertHighlightRange(highlights, 4, 26, 4, 27);  // )
  }

  @Test
  void testCloseParenthesis() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(4, 26); // На ")" в "(1 + 2)"
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext);

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции вложенных скобок
    assertHighlightRange(highlights, 4, 20, 4, 21);  // (
    assertHighlightRange(highlights, 4, 26, 4, 27);  // )
  }

  @Test
  void testOpenSquareBracket() {
    // given
    // Строка 5 (0-based): "    Возврат Массив[0];"
    // "[" на позиции 18, "]" на позиции 20
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(5, 18); // На "[" в "Массив[0]"
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext);

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 5, 18, 5, 19);  // [
    assertHighlightRange(highlights, 5, 20, 5, 21);  // ]
  }

  @Test
  void testCloseSquareBracket() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(5, 20); // На "]" в "Массив[0]"
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext);

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 5, 18, 5, 19);  // [
    assertHighlightRange(highlights, 5, 20, 5, 21);  // ]
  }

  @Test
  void testNonBracket() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var position = new Position(9, 4); // На "Если" (не скобка)
    params.setPosition(position);
    var terminalNodeInfo = DocumentHighlightTestUtils.findTerminalNode(position, documentContext);

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
