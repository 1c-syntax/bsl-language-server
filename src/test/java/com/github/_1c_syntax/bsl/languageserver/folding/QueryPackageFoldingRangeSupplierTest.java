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
package com.github._1c_syntax.bsl.languageserver.folding;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThatFoldingRanges;

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
    assertThatFoldingRanges(foldingRanges)
      .hasSize(3)
      .hasKindAndRange(FoldingRangeKind.Region, 2, 5)
      .hasKindAndRange(FoldingRangeKind.Region, 9, 10)
      .hasKindAndRange(FoldingRangeKind.Region, 14, 17)
      ;
  }

}
