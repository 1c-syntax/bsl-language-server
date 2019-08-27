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

public class IdenticalExpressionsDiagnosticTest  extends AbstractDiagnosticTest<IdenticalExpressionsDiagnostic> {

  IdenticalExpressionsDiagnosticTest() { super(IdenticalExpressionsDiagnostic.class); }

  @Test
  void runTest() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(10);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(4, 9, 4, 25));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(6, 16, 6, 31));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(11, 13, 11, 28));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(13, 9, 13, 66));
    assertThat(diagnostics.get(4).getRange()).isEqualTo(RangeHelper.newRange(15, 16, 15, 34));
    assertThat(diagnostics.get(5).getRange()).isEqualTo(RangeHelper.newRange(19, 9, 19, 85));
    assertThat(diagnostics.get(6).getRange()).isEqualTo(RangeHelper.newRange(21, 16, 21, 33));
    assertThat(diagnostics.get(7).getRange()).isEqualTo(RangeHelper.newRange(25, 9, 25, 38));
    assertThat(diagnostics.get(8).getRange()).isEqualTo(RangeHelper.newRange(27, 16, 27, 43));
    assertThat(diagnostics.get(9).getRange()).isEqualTo(RangeHelper.newRange(31, 16, 31, 33));

  }

}

