/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class SelectionRangeProviderTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/selectionRange.bsl";

  @Autowired
  private SelectionRangeProvider provider;

  @Test
  void test() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());
    var positions = List.of(new Position(18, 10));
    SelectionRangeParams params = new SelectionRangeParams(textDocument, positions);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);
  }

}