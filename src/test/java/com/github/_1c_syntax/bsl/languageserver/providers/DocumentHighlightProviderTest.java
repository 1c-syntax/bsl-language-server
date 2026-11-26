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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentHighlightProviderTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/DocumentHighlightProvider.bsl";

  @Autowired
  private DocumentHighlightProvider provider;

  @Test
  void testProviderReturnsHighlights() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(4, 4)); // На "Если"

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isNotEmpty();
  }

  @Test
  void testProviderReturnsEmptyForNonKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(5, 10)); // На обычном идентификаторе

    // when
    var highlights = provider.getDocumentHighlight(documentContext, params);

    // then
    assertThat(highlights).isEmpty();
  }

  @Test
  void testProviderDelegatesCorrectly() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));

    // when - проверяем разные типы конструкций
    params.setPosition(new Position(4, 4)); // If
    var ifHighlights = provider.getDocumentHighlight(documentContext, params);

    params.setPosition(new Position(14, 4)); // For
    var forHighlights = provider.getDocumentHighlight(documentContext, params);

    params.setPosition(new Position(28, 4)); // Try
    var tryHighlights = provider.getDocumentHighlight(documentContext, params);

    params.setPosition(new Position(35, 1)); // Region
    var regionHighlights = provider.getDocumentHighlight(documentContext, params);

    params.setPosition(new Position(49, 20)); // Bracket
    var bracketHighlights = provider.getDocumentHighlight(documentContext, params);

    params.setPosition(new Position(3, 0)); // Procedure
    var procedureHighlights = provider.getDocumentHighlight(documentContext, params);

    params.setPosition(new Position(39, 4)); // Function
    var functionHighlights = provider.getDocumentHighlight(documentContext, params);

    // then - все должны вернуть результаты
    assertThat(ifHighlights).as("If highlights").isNotEmpty();
    assertThat(forHighlights).as("For highlights").isNotEmpty();
    assertThat(tryHighlights).as("Try highlights").isNotEmpty();
    assertThat(regionHighlights).as("Region highlights").isNotEmpty();
    assertThat(bracketHighlights).as("Bracket highlights").isNotEmpty();
    assertThat(procedureHighlights).as("Procedure highlights").isNotEmpty();
    assertThat(functionHighlights).as("Function highlights").isNotEmpty();
  }
}
