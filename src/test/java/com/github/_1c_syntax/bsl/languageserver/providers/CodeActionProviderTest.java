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

import com.github._1c_syntax.bsl.languageserver.codeactions.QuickFixSupplier;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.CanonicalSpellingKeywordsDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CodeActionProviderTest {

  @Test
  void testGetCodeActions() throws IOException {

    // given
    String filePath = "./src/test/resources/providers/codeAction.bsl";
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(filePath);

    final LanguageServerConfiguration configuration = LanguageServerConfiguration.create();
    DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(configuration);
    QuickFixSupplier quickFixSupplier = new QuickFixSupplier(diagnosticSupplier);
    DiagnosticProvider diagnosticProvider = new DiagnosticProvider(diagnosticSupplier);
    List<Diagnostic> diagnostics = diagnosticProvider.computeDiagnostics(documentContext).stream()
      .filter(diagnostic -> {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(
          CanonicalSpellingKeywordsDiagnostic.class,
          configuration.getLanguage()
        );
        DiagnosticCode diagnosticCode = diagnosticInfo.getCode();
        return diagnostic.getCode().equals(diagnosticCode);
      })
      .collect(Collectors.toList());

    CodeActionProvider codeActionProvider = new CodeActionProvider(diagnosticProvider, quickFixSupplier);

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
      .hasSize(3)
      .extracting(Either::getRight)
      .anyMatch(codeAction -> codeAction.getDiagnostics().contains(diagnostics.get(0)))
      .anyMatch(codeAction -> codeAction.getDiagnostics().contains(diagnostics.get(1)))
      .allMatch(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))
    ;
  }

  @Test
  void testEmptyDiagnosticList() throws IOException {
    // given
    String filePath = "./src/test/resources/providers/codeAction.bsl";
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(filePath);

    DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(LanguageServerConfiguration.create());
    QuickFixSupplier quickFixSupplier = new QuickFixSupplier(diagnosticSupplier);
    DiagnosticProvider diagnosticProvider = new DiagnosticProvider(diagnosticSupplier);
    CodeActionProvider codeActionProvider = new CodeActionProvider(diagnosticProvider, quickFixSupplier);

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
      .hasSize(0);
  }
}