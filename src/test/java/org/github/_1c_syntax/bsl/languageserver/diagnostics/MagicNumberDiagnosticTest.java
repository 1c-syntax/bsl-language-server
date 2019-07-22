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

public class MagicNumberDiagnosticTest extends AbstractDiagnosticTest<MagicNumberDiagnostic> {

  MagicNumberDiagnosticTest() { super(MagicNumberDiagnostic.class); }

  @Test
  void runTest() {
    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(3, 18, 3, 20));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(3, 23, 3, 25));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(7, 31, 7, 33));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(11, 20, 11, 21));
    assertThat(diagnostics.get(4).getRange()).isEqualTo(RangeHelper.newRange(19, 17, 19, 19));
    assertThat(diagnostics.get(5).getRange()).isEqualTo(RangeHelper.newRange(22, 20, 22, 22));
    assertThat(diagnostics.get(6).getRange()).isEqualTo(RangeHelper.newRange(26, 30, 26, 31));
  }

  @Test
  void testConfigure() {
    // conf
    Map<String, Object> configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put("authorizedNumbers", "-1,0,1,60,7");
    getDiagnosticInstance().configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(7, 31, 7, 33));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(11, 20, 11, 21));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(19, 17, 19, 19));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(22, 20, 22, 22));
  }
}
