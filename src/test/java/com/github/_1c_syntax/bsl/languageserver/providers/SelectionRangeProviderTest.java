/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Objects;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThatSelectionRanges;

@SpringBootTest
class SelectionRangeProviderTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/selectionRange.bsl";

  @Autowired
  private SelectionRangeProvider provider;

  private DocumentContext documentContext;

  @BeforeEach
  void init() {
    documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
  }

  @Test
  void testGlobalMethodCallCapturesSemicolon() {
    // given
    var params = selection(18, 10);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .element(0)
      .hasRange(18, 4, 12)
      .hasParentWithRange(18, 4, 17)
    ;
  }

  @Test
  void testStatementCapturesStatementBlockAfterStatement() {
    // given
    var params = selection(4, 19);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .element(0)
      .hasRange(4, 2, 20)
      .extractParent().hasRange(4, 2, 44)
      .extractParent().hasRange(4, 2, 6, 104)
    ;
  }

  @Test
  void testStatementCapturesStatementBlockBeforeStatement() {
    // given
    var params = selection(6, 19);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .element(0)
      .hasRange(6, 2, 20)
      .extractParent().hasRange(6, 2, 104)
      .extractParent().hasRange(4, 2, 6, 104)
    ;
  }

  @Test
  void testStatementCapturesStatementBlockAroundStatement() {
    // given
    var params = selection(5, 19);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .element(0)
      .hasRange(5, 2, 20)
      .extractParent().hasRange(5, 2, 110)
      .extractParent().hasRange(4, 2, 6, 104)
    ;
  }

  @Test
  void testSingleStatementNotCapturesStatementBlock() {
    // given
    var params = selection(18, 10);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .element(0)
      .hasRange(18, 4, 12)
      .extractParent().hasRange(18, 4, 17)
      .extractParent() // codeBlock
      .extractParent() // sub
      .extractParent() // file
      .hasRange(documentContext.getSymbolTree().getModule().getRange())
    ;
  }

  @Test
  void emptySelection() {
    // given
    var params = selection(1, 0);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .allMatch(Objects::isNull)
    ;
  }

  @Test
  void selectionInNestedCall() {
    // given
    var params = selection(5, 70);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .element(0)
      .hasRange(5, 67, 107)
      .extractParent().hasRange(5, 30, 108)
      .extractParent().hasRange(5, 21, 109)
      .extractParent().hasRange(5, 2, 110)
    ;
  }

  @Test
  void ifBranchWithoutElseMatchesIfStatement() {
    // given
    var params = selection(25, 15);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .element(0)
      .hasRange(25, 8, 16)
      .extractParent().hasRange(25, 8, 21)
      .extractParent().hasRange(24, 4, 26, 14)
    ;
  }

  @Test
  void ifBranchWithElseNotMatchIfStatement() {
    // given
    var params = selection(29, 15);

    // when
    var selectionRanges = provider.getSelectionRange(documentContext, params);

    // then
    assertThatSelectionRanges(selectionRanges)
      .hasSize(1)
      .element(0)
      .hasRange(29, 8, 16)
      .extractParent().hasRange(29, 8, 21)
      .extractParent().hasRange(28, 4, 29, 21)
      .extractParent().hasRange(28, 4, 32, 14)
    ;
  }

  private SelectionRangeParams selection(int line, int character) {
    var textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());
    var positions = List.of(new Position(line, character));
    return new SelectionRangeParams(textDocument, positions);
  }
}
