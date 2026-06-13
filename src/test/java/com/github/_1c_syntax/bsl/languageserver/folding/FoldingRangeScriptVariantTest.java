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
package com.github._1c_syntax.bsl.languageserver.folding;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.FoldingRange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Проверяет, что форма ключевого слова в collapsedText (заглушке свёрнутого блока)
 * выбирается по варианту встроенного языка документа (ScriptVariant), а не по языку
 * интерфейса Language Server.
 */
@SpringBootTest
class FoldingRangeScriptVariantTest {

  @Autowired
  private CodeBlockFoldingRangeSupplier codeBlockFoldingRangeSupplier;

  @Autowired
  private RegionFoldingRangeSupplier regionFoldingRangeSupplier;

  @Test
  void methodCollapsedTextUsesRussianKeywordsForRussianScriptVariant() {
    // given
    var documentContext = documentWithScriptVariant(Language.RU);

    // when
    List<FoldingRange> foldingRanges = codeBlockFoldingRangeSupplier.getFoldingRanges(documentContext);

    // then
    assertThat(foldingRanges)
      .extracting(FoldingRange::getCollapsedText)
      .filteredOn(text -> text != null && text.contains("ИмяПроцедуры"))
      .isNotEmpty()
      .allMatch(text -> text.startsWith("Процедура "));
  }

  @Test
  void methodCollapsedTextUsesEnglishKeywordsForEnglishScriptVariant() {
    // given
    var documentContext = documentWithScriptVariant(Language.EN);

    // when
    List<FoldingRange> foldingRanges = codeBlockFoldingRangeSupplier.getFoldingRanges(documentContext);

    // then
    assertThat(foldingRanges)
      .extracting(FoldingRange::getCollapsedText)
      .filteredOn(text -> text != null && text.contains("ИмяПроцедуры"))
      .isNotEmpty()
      .allMatch(text -> text.startsWith("Procedure "));
  }

  @Test
  void regionCollapsedTextUsesRussianKeywordForRussianScriptVariant() {
    // given
    var documentContext = documentWithScriptVariant(Language.RU);

    // when
    List<FoldingRange> foldingRanges = regionFoldingRangeSupplier.getFoldingRanges(documentContext);

    // then
    assertThat(foldingRanges)
      .extracting(FoldingRange::getCollapsedText)
      .filteredOn(text -> text != null && text.contains("ИмяОбласти"))
      .isNotEmpty()
      .allMatch(text -> text.startsWith("Область "));
  }

  @Test
  void regionCollapsedTextUsesEnglishKeywordForEnglishScriptVariant() {
    // given
    var documentContext = documentWithScriptVariant(Language.EN);

    // when
    List<FoldingRange> foldingRanges = regionFoldingRangeSupplier.getFoldingRanges(documentContext);

    // then
    assertThat(foldingRanges)
      .extracting(FoldingRange::getCollapsedText)
      .filteredOn(text -> text != null && text.contains("ИмяОбласти"))
      .isNotEmpty()
      .allMatch(text -> text.startsWith("Region "));
  }

  private static DocumentContext documentWithScriptVariant(Language scriptVariantLanguage) {
    var documentContext = spy(
      TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl"));
    doReturn(scriptVariantLanguage).when(documentContext).getScriptVariantLanguage();
    return documentContext;
  }
}
