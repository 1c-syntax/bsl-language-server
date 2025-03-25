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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.CanonicalSpellingKeywordsDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.StringInterner;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CodeActionProviderTest {

  @Autowired
  private CodeActionProvider codeActionProvider;
  @Autowired
  private LanguageServerConfiguration configuration;
  @Autowired
  private StringInterner stringInterner;

  private DocumentContext documentContext;

  @BeforeEach
  void init() {
    String filePath = "./src/test/resources/providers/codeAction.bsl";
    documentContext = TestUtils.getDocumentContextFromFile(filePath);
  }

  @Test
  void testGetCodeActions() {

    // given
    DiagnosticInfo diagnosticInfo = new DiagnosticInfo(
      CanonicalSpellingKeywordsDiagnostic.class,
      configuration,
      stringInterner);
    DiagnosticCode diagnosticCode = diagnosticInfo.getCode();

    List<Diagnostic> diagnostics = documentContext.getDiagnostics().stream()
      .filter(diagnostic -> diagnostic.getCode().equals(diagnosticCode))
      //  clean diagnostic tags array to emulate clear of tags property from the client
      .peek(diagnostic -> diagnostic.setTags(null))
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
      .allMatch(codeAction -> (codeAction.getDiagnostics().size() == 1) == toBoolean(codeAction.getIsPreferred()))
    ;
  }

  @Test
  void testEmptyDiagnosticList() {
    // given
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

  @Test
  void testOnly() {
    // given
    CodeActionParams params = new CodeActionParams();
    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    DiagnosticInfo diagnosticInfo = new DiagnosticInfo(
      CanonicalSpellingKeywordsDiagnostic.class,
      configuration,
      stringInterner);
    DiagnosticCode diagnosticCode = diagnosticInfo.getCode();

    List<Diagnostic> diagnostics = documentContext.getDiagnostics().stream()
      .filter(diagnostic -> diagnostic.getCode().equals(diagnosticCode))
      .collect(Collectors.toList());

    CodeActionContext codeActionContext = new CodeActionContext();

    codeActionContext.setOnly(List.of(CodeActionKind.Refactor));
    codeActionContext.setDiagnostics(diagnostics);

    params.setRange(new Range());
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<Either<Command, CodeAction>> codeActions = codeActionProvider.getCodeActions(params, documentContext);

    // then
    assertThat(codeActions)
      .extracting(Either::getRight)
      .extracting(CodeAction::getKind)
      .containsOnly(CodeActionKind.Refactor)
    ;
  }

  private static boolean toBoolean(@Nullable Boolean value) {
    if (value == null) {
      return false;
    }
    return value;
  }
}