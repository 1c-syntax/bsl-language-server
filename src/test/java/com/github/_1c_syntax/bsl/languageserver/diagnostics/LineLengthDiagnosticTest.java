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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LineLengthDiagnosticTest extends AbstractDiagnosticTest<LineLengthDiagnostic>{

  LineLengthDiagnosticTest() {
    super(LineLengthDiagnostic.class);
  }

  @Test
  void test() {
    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(4, 0, 4, 121)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(5, 0, 5, 122)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(8, 0, 8, 127)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(11, 0, 11, 136)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(12, 0, 12, 135)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(36, 0, 36, 127)))
    ;
  }

  @Test
  void testConfigure() {
    // given
    Map<String, Object> configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put("maxLineLength", 119);
    getDiagnosticInstance().configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(Ranges.create(3, 0, 3, 120));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(Ranges.create(4, 0, 4, 121));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(Ranges.create(5, 0, 5, 122));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(Ranges.create(8, 0, 8, 127));
    assertThat(diagnostics.get(4).getRange()).isEqualTo(Ranges.create(11, 0, 11, 136));
    assertThat(diagnostics.get(5).getRange()).isEqualTo(Ranges.create(12, 0, 12, 135));

  }
}
