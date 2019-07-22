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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UsingServiceTagDiagnosticTest extends AbstractDiagnosticTest<UsingServiceTagDiagnostic> {

  UsingServiceTagDiagnosticTest() { super(UsingServiceTagDiagnostic.class); }

  @Test
  void runTest()
  {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(15);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(1, 0, 1, 36));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(13, 4, 13, 50));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(21, 4, 21, 29));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(25, 0, 25, 26));
    assertThat(diagnostics.get(4).getRange()).isEqualTo(RangeHelper.newRange(26, 33, 26, 58));
    assertThat(diagnostics.get(5).getRange()).isEqualTo(RangeHelper.newRange(28, 4, 28, 11));
    assertThat(diagnostics.get(6).getRange()).isEqualTo(RangeHelper.newRange(29, 20, 29, 30));
    assertThat(diagnostics.get(7).getRange()).isEqualTo(RangeHelper.newRange(31, 4, 31, 12));
    assertThat(diagnostics.get(8).getRange()).isEqualTo(RangeHelper.newRange(32, 21, 32, 31));
    assertThat(diagnostics.get(9).getRange()).isEqualTo(RangeHelper.newRange(34, 8, 34, 21));
    assertThat(diagnostics.get(10).getRange()).isEqualTo(RangeHelper.newRange(42, 4, 42, 51));
    assertThat(diagnostics.get(11).getRange()).isEqualTo(RangeHelper.newRange(61, 4, 61, 51));
    assertThat(diagnostics.get(12).getRange()).isEqualTo(RangeHelper.newRange(65, 0, 65, 11));
    assertThat(diagnostics.get(13).getRange()).isEqualTo(RangeHelper.newRange(67, 0, 67, 11));
    assertThat(diagnostics.get(14).getRange()).isEqualTo(RangeHelper.newRange(71, 4, 71, 36));

  }
}
