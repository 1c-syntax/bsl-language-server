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

class PairingBrokenTranDiagnosticTest extends AbstractDiagnosticTest<PairingBrokenTranDiagnostic> {

  PairingBrokenTranDiagnosticTest() {
    super(PairingBrokenTranDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(24, 4, 24, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(28, 4, 28, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(36, 4, 36, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(40, 4, 40, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(75, 4, 75, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(78, 4, 78, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(90, 4, 90, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(102, 4, 102, 29)));
  }
}
