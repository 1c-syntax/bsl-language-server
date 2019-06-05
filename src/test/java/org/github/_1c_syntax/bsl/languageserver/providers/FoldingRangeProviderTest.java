/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.FoldingRange;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

class FoldingRangeProviderTest {

  @Test
  void testFoldingRange() throws IOException {

    String fileContent = FileUtils.readFileToString(
      new File("./src/test/resources/providers/foldingRange.bsl"),
      StandardCharsets.UTF_8
    );
    DocumentContext documentContext = new DocumentContext("fake-uri.bsl", fileContent);

    List<FoldingRange> foldingRanges = FoldingRangeProvider.getFoldingRange(documentContext);

    assertThat(foldingRanges).hasSize(12);

    // regions
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getKind().equals("region"))
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
      .filteredOn(foldingRange -> foldingRange.getKind().equals("comment"))
      .hasSize(2)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 9 && foldingRange.getEndLine() == 10)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 21 && foldingRange.getEndLine() == 22)
      ;

    // import
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getKind().equals("imports"))
      .hasSize(1)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 0 && foldingRange.getEndLine() == 1)
    ;

  }
}
