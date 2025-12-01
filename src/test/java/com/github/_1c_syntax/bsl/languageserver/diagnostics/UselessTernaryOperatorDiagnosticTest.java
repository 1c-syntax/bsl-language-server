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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class UselessTernaryOperatorDiagnosticTest extends AbstractDiagnosticTest<UselessTernaryOperatorDiagnostic> {

  UselessTernaryOperatorDiagnosticTest() {
    super(UselessTernaryOperatorDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(1, 4, 1, 26)
      .hasRange(2, 4, 2, 25)
      .hasRange(3, 4, 3, 26)
      .hasRange(4, 4, 4, 25)
      .hasRange(5, 4, 5, 21)
      .hasRange(6, 4, 6, 22)
      .hasRange(7, 4, 7, 19)
      .hasRange(8, 4, 8, 18);

  }

  @Test
  void testQuickFix() {

    final DocumentContext documentContext = getDocumentContext();
    List<Diagnostic> diagnostics = getDiagnostics();

    final Diagnostic directDiagnostic = diagnostics.get(0);
    List<CodeAction> directQuickFixes = getQuickFixes(directDiagnostic);
    assertThat(directQuickFixes).hasSize(1);
    final CodeAction directQuickFix = directQuickFixes.get(0);
    assertThat(directQuickFix)
      .of(diagnosticInstance)
      .in(documentContext)
      .fixes(directDiagnostic)
      .hasChanges(1)
      .hasNewText("Б=1");

    final Diagnostic reversDiagnostic = diagnostics.get(1);
    List<CodeAction> reversQuickFixes = getQuickFixes(reversDiagnostic);
    assertThat(reversQuickFixes).hasSize(1);
    final CodeAction reversQuickFix = reversQuickFixes.get(0);
    assertThat(reversQuickFix)
      .of(diagnosticInstance)
      .in(documentContext)
      .fixes(reversDiagnostic)
      .hasChanges(1)
      .hasNewText("НЕ (Б=0)");
  }

}
