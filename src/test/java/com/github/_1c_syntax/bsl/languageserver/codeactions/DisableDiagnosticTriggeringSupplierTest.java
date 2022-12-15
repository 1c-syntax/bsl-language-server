/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import jakarta.annotation.PostConstruct;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class DisableDiagnosticTriggeringSupplierTest {

  @Autowired
  private LanguageServerConfiguration configuration;
  @Autowired
  private DisableDiagnosticTriggeringSupplier codeActionSupplier;

  @PostConstruct
  public void init() {
    configuration.setLanguage(Language.EN);
  }

  @Test
  void testGetCodeActions() {

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/suppliers/disableDiagnosticTriggering.bsl"
    );

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(documentContext.getDiagnostics());

    CodeActionParams params = new CodeActionParams();
    params.setRange(new Range());
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(11)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable all diagnostic in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable NumberOfValuesInStructureConstructor in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable ExportVariables in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable IfElseDuplicatedCondition in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable CanonicalSpellingKeywords in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable FunctionShouldHaveReturn in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable IfElseIfEndsWithElse in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MagicNumber in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MissingSpace in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MissingVariablesDescription in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable all diagnostic in file"));
  }

  @Test
  void testGetCodeActionsOneLine() {

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/suppliers/disableDiagnosticTriggering.bsl"
    );

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    Range range = Ranges.create(53, 0, 53, 30);

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(
      documentContext
        .getDiagnostics()
        .stream()
        .filter(diagnostic ->
          Ranges.containsRange(range, diagnostic.getRange())
        )
      .collect(Collectors.toList())
    );

    CodeActionParams params = new CodeActionParams();
    params.setRange(range);
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(8)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MagicNumber in line"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MissingSpace in line"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MagicNumber in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MissingSpace in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable all diagnostic in line"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable all diagnostic in file"));
  }

  @Test
  void testGetCodeActionsRegion() {

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/suppliers/disableDiagnosticTriggering.bsl"
    );

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    Range range = Ranges.create(53, 0, 56, 38);

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(
      documentContext
        .getDiagnostics()
        .stream()
        .filter(diagnostic -> Ranges.containsRange(range, diagnostic.getRange()))
        .collect(Collectors.toList())
    );

    CodeActionParams params = new CodeActionParams();
    params.setRange(range);
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(10)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MagicNumber in range"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MissingSpace in range"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MagicNumber in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable MissingSpace in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable all diagnostic in range"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable all diagnostic in file"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable CanonicalSpellingKeywords in range"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable CanonicalSpellingKeywords in file"));
  }

  @Test
  void testGetCodeActionsEmptyFile() {

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/suppliers/disableDiagnosticTriggeringEmpty.bsl"
    );

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(documentContext.getDiagnostics());

    CodeActionParams params = new CodeActionParams();
    params.setRange(new Range());
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(1)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable all diagnostic in file"));
  }

  @Test
  void testNoBslLsDiagnostic() {

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/suppliers/disableDiagnosticTriggeringEmpty.bsl"
    );

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(List.of(new Diagnostic()));

    CodeActionParams params = new CodeActionParams();
    params.setRange(new Range());
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(1)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Disable all diagnostic in file"));
  }
}
