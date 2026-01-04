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
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FoldingRangeProviderTest {

  @Autowired
  private FoldingRangeProvider foldingRangeProvider;

  @Test
  void testFoldingRange() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/foldingRange.bsl");

    List<FoldingRange> foldingRanges = foldingRangeProvider.getFoldingRange(documentContext);

    assertThat(foldingRanges).hasSize(11);

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
}
