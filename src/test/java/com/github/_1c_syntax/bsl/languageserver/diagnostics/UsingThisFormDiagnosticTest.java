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

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class UsingThisFormDiagnosticTest extends AbstractDiagnosticTest<UsingThisFormDiagnostic> {
  private static final String THIS_OBJECT = "ЭтотОбъект";

  UsingThisFormDiagnosticTest() {
    super(UsingThisFormDiagnostic.class);
  }

  @Test
  void runTest() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(15);

    assertThat(diagnostics, true)
      .hasRange(3, 20, 3, 28)
      .hasRange(4, 29, 4, 37)
      .hasRange(5, 4, 5, 12)
      .hasRange(6, 12, 6, 20)
      .hasRange(13, 19, 13, 27)
      .hasRange(14, 20, 14, 28)
      .hasRange(15, 33, 15, 41)
      .hasRange(16, 12, 16, 20)
      .hasRange(40, 16, 40, 24)
      .hasRange(41, 25, 41, 33)
      .hasRange(42, 0, 42, 8)
      .hasRange(44, 76, 44, 84)
      .hasRange(45, 8, 45, 16)
      .hasRange(47, 14, 47, 22)
      .hasRange(47, 24, 47, 32)
    ;
  }

  @Test
  void runQuickFixTest() {
    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(diagnostics.get(0).getRange());

    assertThat(quickFixes).hasSize(1);
    Map<String, List<TextEdit>> changes = quickFixes.get(0).getEdit().getChanges();
    assertThat(changes).hasSize(1);
    assertThat(changes.get("file:///fake-uri.bsl")).allMatch(t -> t.getNewText().equals(THIS_OBJECT));
  }
}