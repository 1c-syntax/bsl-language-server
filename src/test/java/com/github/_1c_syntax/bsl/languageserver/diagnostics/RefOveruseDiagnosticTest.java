/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class RefOveruseDiagnosticTest extends AbstractDiagnosticTest<RefOveruseDiagnostic> {
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  RefOveruseDiagnosticTest() {
    super(RefOveruseDiagnostic.class);
  }

  @Test
  void test() {
    initServerContext(Absolute.path(PATH_TO_METADATA));

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(3, 28, 3, 45)
      .hasRange(13, 8, 13, 34)
      .hasRange(14, 8, 14, 38)
      .hasRange(25, 8, 25, 21)
      .hasRange(37, 8, 37, 29)
      .hasRange(38, 8, 38, 35)
      .hasRange(56, 13, 43)
      .hasRange(57, 14, 48)
      .hasRange(92, 8, 29)
      .hasRange(153, 13, 153, 41)
      .hasRange(164, 13, 164, 53)
      .hasRange(178, 13, 178, 35)
      .hasRange(216, 13, 37)
      .hasRange(226, 13, 37)
      .hasRange(238, 13, 38)
      .hasRange(296, 33, 80)
      .hasRange(300, 33, 70)
      .hasRange(309, 12, 28)
      .hasRange(342, 12, 56)
      .hasRange(343, 12, 56)
      .hasRange(354, 26, 96)
      .hasRange(364, 9, 44) // TODO не должно быть ошибкой
      .hasRange(378, 20, 55) // TODO не должно быть ошибкой
      .hasSize(23);
  }
  @Test
  void testSingleFile() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(3, 28, 3, 45)
      .hasRange(13, 8, 13, 34)
      .hasRange(14, 8, 14, 38)
      .hasRange(25, 8, 25, 21)
      .hasRange(37, 8, 37, 29)
      .hasRange(38, 8, 38, 35)
      .hasRange(56, 13, 43)
      .hasRange(57, 14, 48)
      .hasRange(92, 8, 29)
      .hasRange(153, 13, 153, 41)
      .hasRange(164, 13, 164, 53)
      .hasRange(178, 13, 178, 35)
      .hasRange(216, 13, 37)
      .hasRange(226, 13, 37)
      .hasRange(238, 13, 38)
      .hasRange(296, 33, 80)
      .hasRange(300, 33, 70)
      .hasRange(309, 12, 28)
      .hasRange(342, 12, 56)
      .hasRange(343, 12, 56)
      .hasRange(354, 26, 96)
      .hasRange(364, 9, 44)
      .hasRange(378, 20, 55)
      .hasSize(23);
  }
}
