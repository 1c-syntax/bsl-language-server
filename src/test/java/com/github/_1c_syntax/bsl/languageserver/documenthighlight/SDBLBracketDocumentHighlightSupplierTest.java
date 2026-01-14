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
class SDBLBracketDocumentHighlightSupplierTest {
  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/SDBLBracketDocumentHighlight.bsl";
  // Точные позиции скобок в тестовом файле (0-based):
  // Строка 10: "    |   (Товары.Цена > 0)"
  // "(" на позиции 8, Range(10, 8, 10, 9)
  // ")" на позиции 24, Range(10, 24, 10, 25)
  @Autowired
  private SDBLBracketDocumentHighlightSupplier supplier;
  @Test
  void testOpenParenthesisCursorInside() {
    // given - курсор внутри открывающей скобки
    // Строка 10 (0-based): "    |   (Товары.Цена > 0)"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // Курсор на "(" (строка 10, позиция 8 - на самой скобке)
    params.setPosition(new Position(10, 8));
    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, null);
    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);
    // Проверяем точные позиции
    assertHighlightRange(highlights, 10, 8, 10, 9);   // (
    assertHighlightRange(highlights, 10, 24, 10, 25); // )
  }
  @Test
  void testOpenParenthesisCursorAfter() {
    // given - курсор справа от открывающей скобки
    // Строка 10 (0-based): "    |   (Товары.Цена > 0)"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // Курсор сразу после "(" (строка 10, позиция 9)
    params.setPosition(new Position(10, 9));
    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, null);
    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);
    // Проверяем точные позиции
    assertHighlightRange(highlights, 10, 8, 10, 9);   // (
    assertHighlightRange(highlights, 10, 24, 10, 25); // )
  }
  @Test
  void testCloseParenthesis() {
    // given - курсор на закрывающей скобке
    // Строка 10 (0-based): "    |   (Товары.Цена > 0)"
    // ")" на позиции 24, Range(10, 24, 10, 25)
    // Позиция 25 (конец токена) позволяет найти токен через проверку "курсор после токена"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // Курсор на конец ")" (позиция 25 = конец токена)
    params.setPosition(new Position(10, 25));

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, null);

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 10, 8, 10, 9);   // (
    assertHighlightRange(highlights, 10, 24, 10, 25); // )
  }
  @Test
  void testNonBracket() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 0)); // Позиция в комментарии
    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, null);
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
