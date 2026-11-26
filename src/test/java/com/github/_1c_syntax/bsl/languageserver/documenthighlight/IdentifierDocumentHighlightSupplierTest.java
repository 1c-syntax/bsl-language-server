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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class IdentifierDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE =
    "./src/test/resources/providers/documentHighlight/IdentifierDocumentHighlight.bsl";

  @Autowired
  private IdentifierDocumentHighlightSupplier supplier;

  @Test
  void testLocalVariableHighlights() {
    // given
    var documentContext = getDocumentContext();
    var params = createParams(documentContext, new Position(3, 20)); // обращение к "Переменная"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, findTerminalNode(documentContext, params));

    // then
    assertThat(highlights)
      .extracting(DocumentHighlight::getRange, DocumentHighlight::getKind)
      .containsExactlyInAnyOrder(
        tuple(new Range(new Position(2, 4), new Position(2, 14)), DocumentHighlightKind.Write),
        tuple(new Range(new Position(3, 4), new Position(3, 14)), DocumentHighlightKind.Write),
        tuple(new Range(new Position(3, 17), new Position(3, 27)), DocumentHighlightKind.Read),
        tuple(new Range(new Position(4, 13), new Position(4, 23)), DocumentHighlightKind.Read)
      );
  }

  @Test
  void testParameterHighlights() {
    // given
    var documentContext = getDocumentContext();
    var params = createParams(documentContext, new Position(0, 18)); // объявление параметра "Параметр"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, findTerminalNode(documentContext, params));

    // then
    assertThat(highlights)
      .extracting(DocumentHighlight::getRange, DocumentHighlight::getKind)
      .containsExactlyInAnyOrder(
        tuple(new Range(new Position(0, 15), new Position(0, 23)), DocumentHighlightKind.Write),
        tuple(new Range(new Position(3, 30), new Position(3, 38)), DocumentHighlightKind.Read)
      );
  }

  @Test
  void testMethodHighlights() {
    // given
    var documentContext = getDocumentContext();
    var params = createParams(documentContext, new Position(9, 13)); // вызов метода "Тест"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, findTerminalNode(documentContext, params));

    // then
    assertThat(highlights)
      .extracting(DocumentHighlight::getRange, DocumentHighlight::getKind)
      .containsExactlyInAnyOrder(
        tuple(new Range(new Position(0, 10), new Position(0, 14)), DocumentHighlightKind.Write),
        tuple(new Range(new Position(9, 12), new Position(9, 16)), DocumentHighlightKind.Read)
      );
  }

  @Test
  void testNonIdentifierReturnsEmpty() {
    // given
    var documentContext = getDocumentContext();
    var params = createParams(documentContext, new Position(0, 0)); // ключевое слово "Процедура"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, findTerminalNode(documentContext, params));

    // then
    assertThat(highlights).isEmpty();
  }

  private static DocumentContext getDocumentContext() {
    return TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
  }

  private static DocumentHighlightParams createParams(DocumentContext documentContext, Position position) {
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(position);
    return params;
  }

  private static Optional<DocumentHighlightSupplier.TerminalNodeInfo> findTerminalNode(
    DocumentContext documentContext,
    DocumentHighlightParams params
  ) {
    return DocumentHighlightTestUtils.findTerminalNode(params.getPosition(), documentContext);
  }
}
