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
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyCodeBlockDiagnosticTest extends AbstractDiagnosticTest<EmptyCodeBlockDiagnostic> {

  EmptyCodeBlockDiagnosticTest() {
    super(EmptyCodeBlockDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(9, 4, 9, 9));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(25, 8, 25, 24));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(32, 4, 32, 21));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(42, 0, 42, 16));
    assertThat(diagnostics.get(4).getRange()).isEqualTo(RangeHelper.newRange(44, 0, 44, 21));
    assertThat(diagnostics.get(5).getRange()).isEqualTo(RangeHelper.newRange(45, 4, 45, 9));
  }

  @Test
  void testFileEmptyCodeBlock() {

    List<Diagnostic> diagnostics = getDiagnostics("EmptyCodeBlockDiagnosticFileCodeBlock");

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(4, 4, 4, 16));
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put("commentAsCode", "true");
    getDiagnosticInstance().configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(32, 4, 32, 21)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(44, 0, 44, 21)));
  }

  @Test
  void testConfigureFile() {

    Map<String, Object> configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put("commentAsCode", "true");
    getDiagnosticInstance().configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics("EmptyCodeBlockDiagnosticFileCodeBlock");

    assertThat(diagnostics).hasSize(0);
  }
}
