/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class EmptyRegionDiagnosticTest extends AbstractDiagnosticTest<EmptyRegionDiagnostic> {
  EmptyRegionDiagnosticTest() {
    super(EmptyRegionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 13)
      .hasRange(10, 1, 23)
      .hasRange(12, 1, 26)
    ;

  }

  @Test
  void testQuickFix() {

    final DocumentContext documentContext = getDocumentContext();
    List<Diagnostic> diagnostics = getDiagnostics();
    final Diagnostic externalRegionDiagnostic = diagnostics.get(2);
    final Diagnostic internalRegionDiagnostic = diagnostics.get(1);

    List<CodeAction> quickFixes = getQuickFixes(externalRegionDiagnostic);
    assertThat(quickFixes).hasSize(1);

    final CodeAction quickFix = quickFixes.get(0);

    assertThat(quickFix)
      .of(diagnosticInstance)
      .in(documentContext)
      .fixes(externalRegionDiagnostic);

    assertThat(quickFix)
      .of(diagnosticInstance)
      .in(documentContext)
      .fixes(internalRegionDiagnostic);

    assertThat(quickFix)
      .in(documentContext)
      .hasChanges(1)
      .hasNewText("");

  }
}
