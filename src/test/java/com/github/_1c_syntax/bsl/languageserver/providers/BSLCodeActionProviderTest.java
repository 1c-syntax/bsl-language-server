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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.BSLLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.BSLDocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.CanonicalSpellingKeywordsDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BSLCodeActionProviderTest {

  @Autowired
  private BSLCodeActionProvider codeActionProvider;
  @Autowired
  private BSLLanguageServerConfiguration configuration;

  @Test
  void testGetCodeActions() {

    // given
    String filePath = "./src/test/resources/providers/codeAction.bsl";
    BSLDocumentContext documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = documentContext.getDiagnostics().stream()
      .filter(diagnostic -> {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(
          CanonicalSpellingKeywordsDiagnostic.class,
          configuration
        );
        DiagnosticCode diagnosticCode = diagnosticInfo.getCode();
        return diagnostic.getCode().equals(diagnosticCode);
      })
      .collect(Collectors.toList());

    CodeActionParams params = new CodeActionParams();
    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();

    codeActionContext.setDiagnostics(diagnostics);

    params.setRange(new Range());
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<Either<Command, CodeAction>> codeActions = codeActionProvider.getCodeActions(params, documentContext);

    // then
    assertThat(codeActions)
      .extracting(Either::getRight)
      .hasSizeGreaterThanOrEqualTo(3)
      .anyMatch(codeAction -> codeAction.getDiagnostics().contains(diagnostics.get(0)))
      .anyMatch(codeAction -> codeAction.getDiagnostics().contains(diagnostics.get(1)))
      .anyMatch(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))
    ;
  }

  @Test
  void testEmptyDiagnosticList() {
    // given
    String filePath = "./src/test/resources/providers/codeAction.bsl";
    BSLDocumentContext documentContext = TestUtils.getDocumentContextFromFile(filePath);

    CodeActionParams params = new CodeActionParams();
    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();

    codeActionContext.setDiagnostics(Collections.emptyList());

    params.setRange(new Range());
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<Either<Command, CodeAction>> codeActions = codeActionProvider.getCodeActions(params, documentContext);

    // then
    assertThat(codeActions)
      .filteredOn(codeAction -> codeAction.getRight().getKind().equals(CodeActionKind.QuickFix))
      .isEmpty();
  }
}