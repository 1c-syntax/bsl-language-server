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
class RegionDocumentHighlightSupplierTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/documentHighlight/RegionDocumentHighlight.bsl";

  // Точные позиции токенов в тестовом файле (0-based):
  // Файл содержит BOM, поэтому позиции смещены на 1 символ
  // Строка 2: "#Область ПрограммныйИнтерфейс" - весь контекст (Range: 2, 1, 2, 29)
  // Строка 4: "    #Область ВложеннаяОбласть" - весь контекст (Range: 4, 5, 4, 29)
  // Строка 10: "    #КонецОбласти" - весь контекст (Range: 10, 5, 10, 17)
  // Строка 12: "#КонецОбласти" - весь контекст (Range: 12, 1, 12, 13)

  @Autowired
  private RegionDocumentHighlightSupplier supplier;

  @Test
  void testRegionStart() {
    // given
    // Строка 2 (0-based): "#Область ПрограммныйИнтерфейс"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, 3)); // На "#Область"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, Optional.empty());

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться: #Область и #КонецОбласти
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 2, 1, 2, 29);    // #Область ПрограммныйИнтерфейс
    assertHighlightRange(highlights, 12, 1, 12, 13);  // #КонецОбласти
  }

  @Test
  void testRegionEnd() {
    // given
    // Строка 12 (0-based): "#КонецОбласти"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(12, 5)); // На "#КонецОбласти"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, Optional.empty());

    // then
    assertThat(highlights).isNotEmpty();
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 2, 1, 2, 29);    // #Область ПрограммныйИнтерфейс
    assertHighlightRange(highlights, 12, 1, 12, 13);  // #КонецОбласти
  }

  @Test
  void testNestedRegion() {
    // given
    // Строка 4 (0-based): "    #Область ВложеннаяОбласть"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(4, 6)); // На вложенной "#Область"

    // when
    var highlights = supplier.getDocumentHighlight(params, documentContext, Optional.empty());

    // then
    assertThat(highlights).isNotEmpty();
    // Должны подсветиться только вложенные #Область и #КонецОбласти
    assertThat(highlights).hasSize(2);

    // Проверяем точные позиции
    assertHighlightRange(highlights, 4, 5, 4, 29);    // #Область ВложеннаяОбласть (вложенная)
    assertHighlightRange(highlights, 10, 5, 10, 17);  // #КонецОбласти (вложенная)
  }

  @Test
  void testNonRegionKeyword() {
    // given
    // Строка 15 (0-based): "    Если Истина Тогда"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var params = new DocumentHighlightParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(15, 4)); // На "Если" (не регион)

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
