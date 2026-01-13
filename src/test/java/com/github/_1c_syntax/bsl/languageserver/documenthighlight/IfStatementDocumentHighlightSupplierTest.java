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
class IfStatementDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/IfStatementDocumentHighlight.bsl";

  @Autowired
  private IfStatementDocumentHighlightSupplier supplier;

  @Test
  void testIfKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, 4)); // На "Если"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Если, Тогда, ИначеЕсли, Тогда, Иначе, КонецЕсли
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);
  }

  @Test
  void testElseIfKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(5, 4)); // На "ИначеЕсли"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);
  }

  @Test
  void testElseKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(7, 4)); // На "Иначе"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);
  }

  @Test
  void testEndIfKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(9, 4)); // На "КонецЕсли"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSizeGreaterThanOrEqualTo(6);
  }

  @Test
  void testNonIfKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(13, 4)); // На "Пока" (не if)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isEmpty();
  }
}
