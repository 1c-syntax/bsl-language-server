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
package com.github._1c_syntax.bsl.languageserver.folding;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.FoldingRange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueryPackageFoldingRangeSupplierTest {

  @Autowired
  private QueryPackageFoldingRangeSupplier supplier;

  @Test
  void getFoldingRanges() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/folding/QueryPackageFoldingRangeSupplier.bsl");

    // when
    List<FoldingRange> foldingRanges = supplier.getFoldingRanges(documentContext);

    // then
    assertThat(foldingRanges)
      .hasSize(3)
      .anyMatch(foldingRange -> hasRange(foldingRange, 2, 5))
      .anyMatch(foldingRange -> hasRange(foldingRange, 9, 10))
      .anyMatch(foldingRange -> hasRange(foldingRange, 14, 17))
      ;
  }

  private boolean hasRange(FoldingRange foldingRange, int startLine, int endLine) {
    return foldingRange.getStartLine() == startLine && foldingRange.getEndLine() == endLine;
  }
}