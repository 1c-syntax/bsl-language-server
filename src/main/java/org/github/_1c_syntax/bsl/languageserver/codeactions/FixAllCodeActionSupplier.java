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
package org.github._1c_syntax.bsl.languageserver.codeactions;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.QuickFixProvider;
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FixAllCodeActionSupplier implements CodeActionSupplier {

  private static final int ADD_FIX_ALL_DIAGNOSTICS_THRESHOLD = 2;

  private DiagnosticProvider diagnosticProvider;

  public FixAllCodeActionSupplier(DiagnosticProvider diagnosticProvider) {
    this.diagnosticProvider = diagnosticProvider;
  }

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {
    List<Diagnostic> incomingDiagnostics = params.getContext().getDiagnostics();
    if (incomingDiagnostics.isEmpty()) {
      return Collections.emptyList();
    }

    List<CodeAction> actions = new ArrayList<>();

    incomingDiagnostics.stream()
      .map(Diagnostic::getCode)
      .distinct()
      .flatMap(diagnosticCode -> getFixAllCodeAction(diagnosticCode, params, documentContext).stream())
      .collect(Collectors.toCollection(() -> actions));

    return actions;
  }

  private List<CodeAction> getFixAllCodeAction(
    String diagnosticCode,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    Optional<Class<? extends BSLDiagnostic>> diagnosticClass = DiagnosticProvider.getDiagnosticClass(diagnosticCode);

    if (!diagnosticClass.isPresent()) {
      return Collections.emptyList();
    }

    Class<? extends BSLDiagnostic> bslDiagnosticClass = diagnosticClass.get();

    if (!QuickFixProvider.class.isAssignableFrom(bslDiagnosticClass)) {
      return Collections.emptyList();
    }

    List<Diagnostic> suitableDiagnostics = diagnosticProvider.getComputedDiagnostics(documentContext).stream()
      .filter(diagnostic -> diagnosticCode.equals(diagnostic.getCode()))
      .collect(Collectors.toList());

    // if incomingDiagnostics list is empty - nothing to fix
    // if incomingDiagnostics list has size = 1 - it will be displayed as regular quick fix
    if (suitableDiagnostics.size() < ADD_FIX_ALL_DIAGNOSTICS_THRESHOLD) {
      return Collections.emptyList();
    }

    CodeActionContext fixAllContext = new CodeActionContext();
    fixAllContext.setDiagnostics(suitableDiagnostics);
    fixAllContext.setOnly(Collections.singletonList(CodeActionKind.QuickFix));

    CodeActionParams fixAllParams = new CodeActionParams();
    fixAllParams.setTextDocument(params.getTextDocument());
    fixAllParams.setRange(params.getRange());
    fixAllParams.setContext(fixAllContext);

    QuickFixProvider diagnosticInstance =
      (QuickFixProvider) diagnosticProvider.getDiagnosticInstance(bslDiagnosticClass);

    return diagnosticInstance.getQuickFixes(
      suitableDiagnostics,
      fixAllParams,
      documentContext
    );

  }
}
