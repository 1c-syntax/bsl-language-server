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
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;


class InvalidCharacterInFileDiagnosticTest extends AbstractDiagnosticTest<InvalidCharacterInFileDiagnostic> {
  InvalidCharacterInFileDiagnosticTest() {
    super(InvalidCharacterInFileDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(1, 14, 1, 17)
      .hasRange(2, 15, 2, 18)
      .hasRange(3, 14, 3, 17)
      .hasRange(4, 22, 4, 25)
      .hasRange(5, 20, 5, 23)
      .hasRange(6, 0, 6, 33)
      .hasRange(12, 0, 12, 32)
      .hasRange(14, 15, 14, 18)
    ;

  }

  @Test
  void testQuickFix() {

    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(
      diagnostics.get(3).getRange()
    );

    assertThat(quickFixes)
      .hasSize(1)
      .first()
      .matches(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))

      .matches(codeAction -> codeAction.getDiagnostics().size() == 8)
      .matches(codeAction -> codeAction.getDiagnostics().get(3).equals(diagnostics.get(3)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        !codeAction.getEdit().getChanges().get("file:///fake-uri.bsl").get(3).getNewText().contains("—")
      )
    ;

    quickFixes = getQuickFixes(
      diagnostics.get(7).getRange()
    );

    assertThat(quickFixes)
      .hasSize(1)
      .first()
      .matches(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))

      .matches(codeAction -> codeAction.getDiagnostics().size() == 8)
      .matches(codeAction -> codeAction.getDiagnostics().get(7).equals(diagnostics.get(7)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        !codeAction.getEdit().getChanges().get("file:///fake-uri.bsl").get(7).getNewText().contains(" ")
      )
    ;
  }
}
