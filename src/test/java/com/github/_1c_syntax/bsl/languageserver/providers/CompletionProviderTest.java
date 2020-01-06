/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionProviderTest {

  @Test
  void getCompletion() {

    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/completion.bsl");

    var position = new Position(3, 0);
    var textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());

    var params = new CompletionParams();
    params.setPosition(position);
    params.setTextDocument(textDocument);

    var completionList = CompletionProvider.getCompletion(documentContext, params).getRight().getItems();

    assertThat(completionList).hasSize(2);

    assertThat(completionList)
      .filteredOn(completionItem -> completionItem.getKind() == CompletionItemKind.Method)
      .hasSize(2);

    assertThat(completionList)
      .anyMatch(completionItem -> completionItem.getLabel().equals("А"))
      .anyMatch(completionItem -> completionItem.getLabel().equals("Б"))
      .filteredOn(completionItem -> completionItem.getDocumentation() != null)
      .anyMatch(completionItem -> completionItem.getDocumentation().getRight().getValue().contains("Описание функции"))
    ;

  }

}
