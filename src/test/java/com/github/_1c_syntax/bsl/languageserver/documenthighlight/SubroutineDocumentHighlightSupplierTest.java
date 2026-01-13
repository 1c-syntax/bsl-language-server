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
class SubroutineDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/SubroutineDocumentHighlight.bsl";

  @Autowired
  private SubroutineDocumentHighlightSupplier supplier;

  @Test
  void testProcedureKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, 0)); // На "Процедура"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Процедура и КонецПроцедуры
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testEndProcedureKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(4, 0)); // На "КонецПроцедуры"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testFunctionKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(6, 0)); // На "Функция"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: Функция и КонецФункции
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testEndFunctionKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(8, 0)); // На "КонецФункции"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);
  }

  @Test
  void testNonSubroutineKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(16, 4)); // На "Если" (не процедура)

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext);

    // then
    assertThat(highlights).isEmpty();
  }
}

