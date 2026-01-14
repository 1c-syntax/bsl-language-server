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
class SDBLJoinDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/SDBLJoinDocumentHighlight.bsl";

  @Autowired
  private SDBLJoinDocumentHighlightSupplier supplier;

  @Test
  void testLeftKeyword() {
    // given
    // Строка 10 (0-based): "    |ЛЕВОЕ СОЕДИНЕНИЕ..."
    // "ЛЕВОЕ" начинается с позиции 5 (после "    |")
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(10, 6)); // На "ЛЕВОЕ"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: ЛЕВОЕ, СОЕДИНЕНИЕ, ПО (первого JOIN)
    assertThat(highlights).hasSize(3);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 10, 5, 10, 10);   // ЛЕВОЕ
    assertHighlightRange(highlights, 10, 11, 10, 21);  // СОЕДИНЕНИЕ
    assertHighlightRange(highlights, 11, 8, 11, 10);   // ПО
  }

  @Test
  void testJoinKeyword() {
    // given
    // Строка 10 (0-based): "    |ЛЕВОЕ СОЕДИНЕНИЕ..."
    // "СОЕДИНЕНИЕ" начинается с позиции 11
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(10, 15)); // На "СОЕДИНЕНИЕ"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(3);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 10, 5, 10, 10);   // ЛЕВОЕ
    assertHighlightRange(highlights, 10, 11, 10, 21);  // СОЕДИНЕНИЕ
    assertHighlightRange(highlights, 11, 8, 11, 10);   // ПО
  }

  @Test
  void testOnKeyword() {
    // given
    // Строка 11 (0-based): "    |   ПО Товары.Поставщик..."
    // "ПО" начинается с позиции 8 (после "    |   ")
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(11, 8)); // На "ПО"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(3);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 10, 5, 10, 10);   // ЛЕВОЕ
    assertHighlightRange(highlights, 10, 11, 10, 21);  // СОЕДИНЕНИЕ
    assertHighlightRange(highlights, 11, 8, 11, 10);   // ПО
  }

  @Test
  void testNonJoinKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 0)); // Позиция в комментарии

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

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
