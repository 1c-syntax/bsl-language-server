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

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeCapabilities;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeSupportCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class FoldingRangeProviderTest {

  @Autowired
  private FoldingRangeProvider foldingRangeProvider;

  @MockitoSpyBean
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  @BeforeEach
  void beforeEach() {
    setCapabilities(false, null);
  }

  @AfterEach
  void afterEach() {
    setCapabilities(false, null);
  }

  @Test
  void testFoldingRange() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl");

    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    assertThat(foldingRanges).hasSize(13);

    // regions
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getKind().equals(FoldingRangeKind.Region))
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 3 && foldingRange.getEndLine() == 26)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 5 && foldingRange.getEndLine() == 19)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 7 && foldingRange.getEndLine() == 17)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 11 && foldingRange.getEndLine() == 15)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 12 && foldingRange.getEndLine() == 14)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 23 && foldingRange.getEndLine() == 24)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 28 && foldingRange.getEndLine() == 29)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 35 && foldingRange.getEndLine() == 37)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 39 && foldingRange.getEndLine() == 41)
    ;


    // comments
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getKind().equals(FoldingRangeKind.Comment))
      .hasSize(2)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 9 && foldingRange.getEndLine() == 10)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 21 && foldingRange.getEndLine() == 22)
    ;

    // import
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getKind().equals(FoldingRangeKind.Imports))
      .hasSize(1)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 0 && foldingRange.getEndLine() == 1)
    ;

  }

  @Test
  void testFoldingRangeParseError() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRangeParseError.bsl");
    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    assertThat(foldingRanges).isEmpty();

  }

  @Test
  void testCollapsedTextForRegionWhenSupported() {

    // given
    setCapabilities(true, null);
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl");

    // when
    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    // then
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getStartLine() == 3 && foldingRange.getEndLine() == 26)
      .hasSize(1)
      .allMatch(foldingRange -> foldingRange.getCollapsedText().contains("Имя области"));
  }

  @Test
  void testCollapsedTextForRegionSplitsNameIntoWords() {

    // given
    setCapabilities(true, null);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/foldingRangeRegionName.bsl");

    // when
    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    // then
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getKind().equals(FoldingRangeKind.Region))
      .extracting(FoldingRange::getCollapsedText)
      .contains("Область Служебные процедуры и функции");
  }

  @Test
  void testCollapsedTextForMethodWhenSupported() {

    // given
    setCapabilities(true, null);
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl");

    // when
    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    // then
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getStartLine() == 11 && foldingRange.getEndLine() == 15)
      .hasSize(1)
      .allMatch(foldingRange -> foldingRange.getCollapsedText().contains("ИмяПроцедуры"));
  }

  @Test
  void testCollapsedTextNotSetWhenNotSupported() {

    // given
    setCapabilities(false, null);
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl");

    // when
    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    // then: границы диапазонов не меняются, текст-заглушка не выставляется
    assertThat(foldingRanges).hasSize(13);
    assertThat(foldingRanges)
      .allMatch(foldingRange -> foldingRange.getCollapsedText() == null);
  }

  @Test
  void testRangeLimitTruncatesToPrioritizedRanges() {

    // given: клиент ограничивает число областей значением меньше, чем их вычислено (13)
    setCapabilities(false, 3);
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl");

    // when
    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    // then: возвращено ровно rangeLimit областей, и это самые крупные (внешние/верхнеуровневые)
    assertThat(foldingRanges).hasSize(3);
    assertThat(foldingRanges)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 3 && foldingRange.getEndLine() == 26)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 5 && foldingRange.getEndLine() == 19)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 7 && foldingRange.getEndLine() == 17);

    // and: мелкие вложенные области отброшены первыми
    assertThat(foldingRanges)
      .noneMatch(foldingRange -> foldingRange.getStartLine() == 12 && foldingRange.getEndLine() == 14);
  }

  @Test
  void testRangeLimitNotAppliedWhenAbsent() {

    // given: клиент не заявил лимит
    setCapabilities(false, null);
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl");

    // when
    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    // then: возвращены все области без изменений
    assertThat(foldingRanges).hasSize(13);
  }

  @Test
  void testRangeLimitNotAppliedWhenListWithinLimit() {

    // given: лимит больше числа вычисленных областей
    setCapabilities(false, 100);
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl");

    // when
    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    // then: возвращены все области без изменений
    assertThat(foldingRanges).hasSize(13);
  }

  /**
   * Подменяет заявленные клиентом возможности секции {@code textDocument.foldingRange}
   * в {@link ClientCapabilitiesHolder} и пересчитывает их кэш в провайдере
   * через {@code handleInitializeEvent}.
   *
   * @param collapsedTextSupported поддержка возможности {@code foldingRange.collapsedText}
   * @param rangeLimit             значение {@code rangeLimit} ({@code null} — лимит не заявлен)
   */
  private void setCapabilities(boolean collapsedTextSupported, @Nullable Integer rangeLimit) {
    var foldingRangeSupportCapabilities = new FoldingRangeSupportCapabilities(collapsedTextSupported);
    var foldingRangeCapabilities = new FoldingRangeCapabilities();
    foldingRangeCapabilities.setFoldingRange(foldingRangeSupportCapabilities);
    foldingRangeCapabilities.setRangeLimit(rangeLimit);
    var textDocumentClientCapabilities = new TextDocumentClientCapabilities();
    textDocumentClientCapabilities.setFoldingRange(foldingRangeCapabilities);
    var clientCapabilities = new ClientCapabilities();
    clientCapabilities.setTextDocument(textDocumentClientCapabilities);
    when(clientCapabilitiesHolder.getCapabilities()).thenReturn(Optional.of(clientCapabilities));
    foldingRangeProvider.handleInitializeEvent();
  }
}
