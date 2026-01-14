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
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
class SDBLCaseDocumentHighlightSupplierTest {
  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/SDBLCaseDocumentHighlight.bsl";
  // Точные позиции токенов в тестовом файле (0-based):
  // Строка 7: "|   ВЫБОР" - ВЫБОР на позиции 8-12 (Range: 7, 8, 7, 13)
  // Строка 8: "|       КОГДА ... ТОГДА" - КОГДА на 12-16, ТОГДА на 37-41
  // Строка 9: "|       КОГДА ... ТОГДА" - КОГДА на 12-16, ТОГДА на 36-40
  // Строка 10: "|       ИНАЧЕ" - ИНАЧЕ на 12-16
  // Строка 11: "|   КОНЕЦ" - КОНЕЦ на 8-12
  @Autowired
  private SDBLCaseDocumentHighlightSupplier supplier;
  @Test
  void testCaseKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // Курсор на "ВЫБОР" (строка 7, позиция 9 - внутри слова)
    params.setPosition(new Position(7, 9));
    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, Optional.empty());
    // then
    assertThat(highlights).isNotEmpty();
    // ВЫБОР, КОГДА (2 раза), ТОГДА (2 раза), ИНАЧЕ, КОНЕЦ = 7 элементов
    assertThat(highlights).hasSize(7);
    // Проверяем точные позиции ключевых элементов
    assertHighlightRange(highlights, 7, 8, 7, 13);    // ВЫБОР
    assertHighlightRange(highlights, 8, 12, 8, 17);   // первый КОГДА
    assertHighlightRange(highlights, 9, 12, 9, 17);   // второй КОГДА
    assertHighlightRange(highlights, 10, 12, 10, 17); // ИНАЧЕ
    assertHighlightRange(highlights, 11, 8, 11, 13);  // КОНЕЦ
  }
  @Test
  void testCaseKeywordCursorAfterToken() {
    // given - курсор справа от "ВЫБОР"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // Курсор сразу после "ВЫБОР" (строка 7, позиция 13)
    params.setPosition(new Position(7, 13));
    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, Optional.empty());
    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(7);
    assertHighlightRange(highlights, 7, 8, 7, 13);    // ВЫБОР
  }
  @Test
  void testWhenKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // Курсор на первом "КОГДА" (строка 8, позиция 13 - внутри слова)
    params.setPosition(new Position(8, 13));
    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, Optional.empty());
    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(7);
    // Проверяем точные позиции
    assertHighlightRange(highlights, 7, 8, 7, 13);    // ВЫБОР
    assertHighlightRange(highlights, 8, 12, 8, 17);   // первый КОГДА
    assertHighlightRange(highlights, 11, 8, 11, 13);  // КОНЕЦ
  }
  @Test
  void testEndKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // Курсор на "КОНЕЦ" (строка 11, позиция 9 - внутри слова)
    params.setPosition(new Position(11, 9));
    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, Optional.empty());
    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(7);
    // Проверяем точные позиции
    assertHighlightRange(highlights, 7, 8, 7, 13);    // ВЫБОР
    assertHighlightRange(highlights, 11, 8, 11, 13);  // КОНЕЦ
  }
  @Test
  void testNonCaseKeyword() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 0)); // Позиция в комментарии
    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, Optional.empty());
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
