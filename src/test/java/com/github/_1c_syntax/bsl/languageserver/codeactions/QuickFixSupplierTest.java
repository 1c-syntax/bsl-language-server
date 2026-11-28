/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QuickFixSupplierTest {

  private static final DiagnosticCode COMMENTED_CODE = new DiagnosticCode("CommentedCode");

  @Autowired
  private QuickFixSupplier quickFixSupplier;

  private DocumentContext documentContext;
  private List<Diagnostic> commentedCode;
  private CodeActionParams params;

  @BeforeEach
  void setUp() {
    documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/diagnostics/CommentedCodeDiagnostic.bsl");
    commentedCode = documentContext.getDiagnostics().stream()
      .filter(diagnostic -> COMMENTED_CODE.equals(diagnostic.getCode()))
      .toList();

    params = new CodeActionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setRange(Ranges.create());
    params.setContext(new CodeActionContext(commentedCode));
  }

  @Test
  void quickFixesForDiagnosticWithQuickFixProvider() {
    assertThat(commentedCode).hasSizeGreaterThan(1);

    var quickFixes = quickFixSupplier.getQuickFixes(commentedCode.getFirst(), params, documentContext);

    assertThat(quickFixes)
      .isNotEmpty()
      .allMatch(codeAction -> codeAction.getDiagnostics().equals(List.of(commentedCode.getFirst())));
  }

  @Test
  void noQuickFixesForUnknownOrNonFixableCode() {
    var unknown = new Diagnostic(Ranges.create(), "message");
    unknown.setCode(new DiagnosticCode("NON_EXISTING"));
    var nonFixable = new Diagnostic(Ranges.create(), "message");
    nonFixable.setCode(new DiagnosticCode("CommitTransactionOutsideTryCatch"));

    assertThat(quickFixSupplier.getQuickFixes(unknown, params, documentContext)).isEmpty();
    assertThat(quickFixSupplier.getQuickFixes(nonFixable, params, documentContext)).isEmpty();
  }

  @Test
  void fixAllQuickFixesCoverAllDiagnosticsOfCode() {
    var quickFixes = quickFixSupplier.getFixAllQuickFixes(COMMENTED_CODE, commentedCode, params, documentContext);

    assertThat(quickFixes)
      .isNotEmpty()
      .allMatch(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))
      .anyMatch(codeAction -> codeAction.getDiagnostics().containsAll(commentedCode));
  }
}
