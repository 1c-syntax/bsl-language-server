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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.FAKE_DOCUMENT_URI;


class InvalidCharacterInFileDiagnosticTest extends AbstractDiagnosticTest<InvalidCharacterInFileDiagnostic> {
  InvalidCharacterInFileDiagnosticTest() {
    super(InvalidCharacterInFileDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(1, 14, 1, 17)
      .hasRange(2, 15, 2, 18)
      .hasRange(3, 14, 3, 17)
      .hasRange(4, 22, 4, 25)
      .hasRange(5, 20, 5, 23)
      .hasRange(6, 0, 6, 33)
      .hasRange(12, 0, 12, 32)
      .hasRange(14, 15, 14, 18)
      .hasRange(17, 0, 17, 1)
      .hasRange(20, 0, 20, 1)
      .hasRange(22, 0, 22, 1)
      .hasRange(24, 0, 24, 1)
      .hasRange(26, 0, 26, 1)
      .hasRange(28, 0, 28, 1)
      .hasSize(14);

  }

  @Test
  void testMultiString() {
    String module = "//в строке ниже неразрывный пробел\n" +
      "А = \" \n" +
      "|// минусы с ошибками\n" +
      "|//СреднееТире = \n" +
      "|–;\n" +
      "|//ЦифровоеТире = \n" +
      "|‒;\n" +
      "|//ДлинноеТире = \n" +
      "|—;\n" +
      "|//ГоризонтальнаяЛиния = \n" +
      "|―;\n" +
      "|//НеправильныйМинус = \n" +
      "|−;\";\n";

    var documentContext = TestUtils.getDocumentContext(module);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .hasRange(1, 4, 1, 6)
      .hasRange(4, 0, 4, 3)
      .hasRange(6, 0, 6, 3)
      .hasRange(8, 0, 8, 3)
      .hasRange(10, 0, 10, 3)
      .hasRange(12, 0, 12, 4)
      .hasSize(6);
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

      .matches(codeAction -> codeAction.getDiagnostics().size() == 14)
      .matches(codeAction -> codeAction.getDiagnostics().get(3).equals(diagnostics.get(3)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        !codeAction.getEdit().getChanges().get(FAKE_DOCUMENT_URI.toString()).get(3).getNewText().contains("—")
      )
    ;

    quickFixes = getQuickFixes(
      diagnostics.get(7).getRange()
    );

    assertThat(quickFixes)
      .hasSize(1)
      .first()
      .matches(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))

      .matches(codeAction -> codeAction.getDiagnostics().size() == 14)
      .matches(codeAction -> codeAction.getDiagnostics().get(7).equals(diagnostics.get(7)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        !codeAction.getEdit().getChanges().get(FAKE_DOCUMENT_URI.toString()).get(7).getNewText().contains(" ")
      )
    ;
  }
}
