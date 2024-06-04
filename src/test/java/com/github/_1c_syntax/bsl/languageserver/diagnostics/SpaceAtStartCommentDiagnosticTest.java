/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.FAKE_DOCUMENT_URI;


class SpaceAtStartCommentDiagnosticTest extends AbstractDiagnosticTest<SpaceAtStartCommentDiagnostic> {

  SpaceAtStartCommentDiagnosticTest() {
    super(SpaceAtStartCommentDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics, true)
      .hasRange(6, 0, 6, 20)
      .hasRange(8, 12, 8, 26)
      .hasRange(9, 16, 9, 32)
      .hasRange(20, 0, 20, 56)
      .hasRange(22, 0, 56)
      .hasRange(34, 0, 20)
      .hasRange(35, 0, 19)
    ;
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("commentsAnnotation", "//@,//(c),//(с)");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics, true)
      .hasRange(6, 0, 6, 20)
      .hasRange(8, 12, 8, 26)
      .hasRange(9, 16, 9, 32)
      .hasRange(22, 0, 56)
      .hasRange(28, 0, 81)
      .hasRange(34, 0, 20)
      .hasRange(35, 0, 19)
    ;
  }

  @Test
  void testConfigureStrict() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("useStrictValidation", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(6, 0, 20)
      .hasRange(8, 12, 26)
      .hasRange(9, 16, 32)
      .hasRange(20, 0, 56)
      .hasRange(35, 0, 19)
    ;
  }

  @Test
  void testQuickFixStartLine() {

    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(
      diagnostics.get(0),
      Ranges.create(6, 10, 6, 20)
    );

    assertThat(quickFixes)
      .hasSize(1)
      .first()
      .matches(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))

      .matches(codeAction -> codeAction.getDiagnostics().size() == 1)
      .matches(codeAction -> codeAction.getDiagnostics().get(0).equals(diagnostics.get(0)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        codeAction.getEdit().getChanges().get(FAKE_DOCUMENT_URI.toString()).get(0).getNewText().startsWith("// ")
      );
  }

  @Test
  void testQuickFixInLine() {

    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(
      diagnostics.get(1),
      Ranges.create(8, 12, 8, 26)
    );

    assertThat(quickFixes)
      .hasSize(1)
      .first()
      .matches(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))

      .matches(codeAction -> codeAction.getDiagnostics().size() == 1)
      .matches(codeAction -> codeAction.getDiagnostics().get(0).equals(diagnostics.get(1)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        codeAction.getEdit().getChanges().get(FAKE_DOCUMENT_URI.toString()).get(0).getNewText().startsWith("// ")
      );
  }
}
