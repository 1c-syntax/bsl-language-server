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

    assertThat(diagnostics).hasSize(21);
    assertThat(diagnostics)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(27, 4, 27, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(40, 4, 40, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(44, 4, 44, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(52, 4, 52, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(53, 4, 53, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(56, 4, 56, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(83, 4, 83, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(87, 4, 87, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(88, 4, 88, 24)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(101, 4, 101, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(93, 8, 93, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(95, 8, 95, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(97, 8, 97, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(113, 4, 113, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(106, 8, 106, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(110, 8, 110, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(108, 8, 108, 26)));

    assertThat(diagnostics)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(31, 4, 31, 22))
        && diagnostic.getMessage().matches(".*CommitTransaction.*"))
      .anyMatch(diagnostic -> (diagnostic.getRange().equals(RangeHelper.newRange(31, 4, 31, 22))
        && diagnostic.getMessage().matches(".*RollbackTransaction.*")))
      .anyMatch(diagnostic -> (diagnostic.getRange().equals(RangeHelper.newRange(45, 4, 45, 22))
        && diagnostic.getMessage().matches(".*ЗафиксироватьТранзакцию.*")))
      .anyMatch(diagnostic -> (diagnostic.getRange().equals(RangeHelper.newRange(45, 4, 45, 22))
        && diagnostic.getMessage().matches(".*ОтменитьТранзакцию.*")));
  }
}
