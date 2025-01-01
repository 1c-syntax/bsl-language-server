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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PairingBrokenTransactionDiagnosticTest extends AbstractDiagnosticTest<PairingBrokenTransactionDiagnostic> {

  PairingBrokenTransactionDiagnosticTest() {
    super(PairingBrokenTransactionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics)
      .hasSize(21)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(27, 4, 27, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(40, 4, 40, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(44, 4, 44, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(52, 4, 52, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(53, 4, 53, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(56, 4, 56, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(83, 4, 83, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(87, 4, 87, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(88, 4, 88, 24)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(101, 4, 101, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(93, 8, 93, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(95, 8, 95, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(97, 8, 97, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(113, 4, 113, 29)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(106, 8, 106, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(110, 8, 110, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(108, 8, 108, 26)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(31, 4, 31, 22))
        && diagnostic.getMessage().matches(".*CommitTransaction.*"))
      .anyMatch(diagnostic -> (diagnostic.getRange().equals(Ranges.create(31, 4, 31, 22))
        && diagnostic.getMessage().matches(".*RollbackTransaction.*")))
      .anyMatch(diagnostic -> (diagnostic.getRange().equals(Ranges.create(45, 4, 45, 22))
        && diagnostic.getMessage().matches(".*ЗафиксироватьТранзакцию.*")))
      .anyMatch(diagnostic -> (diagnostic.getRange().equals(Ranges.create(45, 4, 45, 22))
        && diagnostic.getMessage().matches(".*ОтменитьТранзакцию.*")));
  }
}
