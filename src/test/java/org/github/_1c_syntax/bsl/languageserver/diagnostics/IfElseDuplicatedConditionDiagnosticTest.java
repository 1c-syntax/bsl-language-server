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

public class IfElseDuplicatedConditionDiagnosticTest extends AbstractDiagnosticTest<IfElseDuplicatedConditionDiagnostic> {

  IfElseDuplicatedConditionDiagnosticTest() {
    super(IfElseDuplicatedConditionDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(5, 10, 5, 15));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(9, 10, 9, 21));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(25, 10, 25, 15));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(22, 13, 22, 18));
  }
}
