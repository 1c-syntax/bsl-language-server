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
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CompletionProviderTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void testDotCompletionOnArray() {
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // ДругоеИмяМассива.Добавить(1); — позиция сразу после точки на строке 19 (line 18)
    params.setPosition(new Position(18, 17));

    var items = completionProvider.getCompletion(documentContext, params);

    assertThat(items)
      .isNotEmpty()
      .extracting(CompletionItem::getLabel)
      .contains("Добавить");
  }

  @Test
  void testNoCompletionWithoutDot() {
    var content = "А = 1;\n";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 4));

    var items = completionProvider.getCompletion(documentContext, params);
    assertThat(items).isEmpty();
  }
}
