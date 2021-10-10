/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.FAKE_DOCUMENT_URI;


class EmptyStatementDiagnosticTest extends AbstractDiagnosticTest<EmptyStatementDiagnostic> {

  EmptyStatementDiagnosticTest() {
    super(EmptyStatementDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(1, 18, 1, 19)
      .hasRange(2, 8, 2, 9);

  }

  @Test
  void testQuickFix() {

    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(
      diagnostics.get(0),
      Ranges.create(3, 19, 3, 19)
    );

    assertThat(quickFixes)
      .hasSize(1)
      .first()
      .matches(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))

      .matches(codeAction -> codeAction.getDiagnostics().size() == 1)
      .matches(codeAction -> codeAction.getDiagnostics().get(0).equals(diagnostics.get(0)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        codeAction.getEdit().getChanges().get(FAKE_DOCUMENT_URI.toString()).get(0).getNewText().equals("")
      )
    ;
  }
}
