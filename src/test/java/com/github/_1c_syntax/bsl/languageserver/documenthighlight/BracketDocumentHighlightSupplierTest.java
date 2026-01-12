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
class BracketDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight.bsl";

  @Autowired
  private BracketDocumentHighlightSupplier supplier;

  @Test
  void testOpenParenthesis() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(49, 24)); // На "(" в "(1 + 2)"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться обе круглые скобки
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testCloseParenthesis() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(49, 29)); // На ")" в "(1 + 2)"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testOpenSquareBracket() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(50, 18)); // На "[" в "Массив[0]"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться обе квадратные скобки
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testCloseSquareBracket() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(50, 20)); // На "]" в "Массив[0]"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testNonBracket() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, 6)); // На "Если" (не скобка)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isEmpty();
  }
}
