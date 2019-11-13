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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class CommentedCodeDiagnosticTest extends AbstractDiagnosticTest<CommentedCodeDiagnostic> {

  CommentedCodeDiagnosticTest() {
    super(CommentedCodeDiagnostic.class);
  }

  @Test
  void runTest()
  {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(11);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(Ranges.create(0, 0, 6, 81));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(Ranges.create(16, 4, 34, 16));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(Ranges.create(36, 4, 42, 156));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(Ranges.create(44, 4, 49, 16));
    assertThat(diagnostics.get(4).getRange()).isEqualTo(Ranges.create(59, 4, 65, 78));
    assertThat(diagnostics.get(5).getRange()).isEqualTo(Ranges.create(76, 0, 80, 18));
    assertThat(diagnostics.get(6).getRange()).isEqualTo(Ranges.create(82, 0, 82, 23));
    assertThat(diagnostics.get(7).getRange()).isEqualTo(Ranges.create(84, 0, 85, 38));
    assertThat(diagnostics.get(8).getRange()).isEqualTo(Ranges.create(117, 0, 118, 24));
    assertThat(diagnostics.get(9).getRange()).isEqualTo(Ranges.create(203, 0, 203, 32));
    assertThat(diagnostics.get(10).getRange()).isEqualTo(Ranges.create(244, 0, 264, 152));
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultDiagnosticConfiguration();
    configuration.put("threshold", 1f);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(0);

  }

  @Test
  void testQuickFixRemoveCode(){
    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(diagnostics.get(0));

    assertThat(quickFixes)
      .hasSize(1);

    final CodeAction fix = quickFixes.get(0);
    assertThat(fix).of(diagnosticInstance).in(getDocumentContext()).fixes(diagnostics.get(0));

  }
}