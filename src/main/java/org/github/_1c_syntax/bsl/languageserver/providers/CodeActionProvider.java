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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.QuickFixProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CodeActionProvider {

  private final DiagnosticProvider diagnosticProvider;

  public CodeActionProvider(DiagnosticProvider diagnosticProvider) {
    this.diagnosticProvider = diagnosticProvider;
  }

  public static List<CodeAction> createCodeActions(
    Range range,
    String newText,
    String title,
    String uri,
    Diagnostic diagnostic
  ) {

    Map<String, List<TextEdit>> changes = new HashMap<>();
    TextEdit textEdit = new TextEdit(range, newText);

    changes.put(uri, Collections.singletonList(textEdit));

    WorkspaceEdit edit = new WorkspaceEdit();

    edit.setChanges(changes);

    CodeAction codeAction = new CodeAction(title);
    codeAction.setDiagnostics(Collections.singletonList(diagnostic));
    codeAction.setEdit(edit);
    codeAction.setKind(CodeActionKind.QuickFix);

    return Collections.singletonList(codeAction);
  }

  public List<Either<Command, CodeAction>> getCodeActions(
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    List<Either<Command, CodeAction>> codeActions = new ArrayList<>();

    List<Diagnostic> incomingDiagnostics = params.getContext().getDiagnostics();
    if (!incomingDiagnostics.isEmpty()) {

      incomingDiagnostics.stream()
        .map(Diagnostic::getCode)
        .distinct()
        .forEach((String diagnosticCode) -> {
          Optional<Class<? extends BSLDiagnostic>> diagnosticClass
            = DiagnosticProvider.getDiagnosticClass(diagnosticCode);

          diagnosticClass.ifPresent((Class<? extends BSLDiagnostic> bslDiagnosticClass) -> {
            if (!QuickFixProvider.class.isAssignableFrom(bslDiagnosticClass)) {
              return;
            }

            List<Diagnostic> suitableDiagnostics = diagnosticProvider.getComputedDiagnostics(documentContext).stream()
              .filter(diagnostic -> diagnosticCode.equals(diagnostic.getCode()))
              .collect(Collectors.toList());

            long diagnosticsWithTheSameCode = suitableDiagnostics.stream()
              .filter(diagnostic -> diagnosticCode.equals(diagnostic.getCode()))
              .count();

            // if incomingDiagnostics list is empty - nothing to fix
            // if incomingDiagnostics list has size = 1 - it will be displayed as regular quick fix
            final int ADD_FIX_ALL_DIAGNOSTICS_THRESHOLD = 2;

            if (diagnosticsWithTheSameCode < ADD_FIX_ALL_DIAGNOSTICS_THRESHOLD) {
              return;
            }

            CodeActionContext fixAllContext = new CodeActionContext();
            fixAllContext.setDiagnostics(suitableDiagnostics);
            fixAllContext.setOnly(Collections.singletonList(CodeActionKind.QuickFix));

            CodeActionParams fixAllParams = new CodeActionParams();
            fixAllParams.setTextDocument(params.getTextDocument());
            fixAllParams.setRange(params.getRange());
            fixAllParams.setContext(fixAllContext);

            BSLDiagnostic diagnosticInstance = diagnosticProvider.getDiagnosticInstance(bslDiagnosticClass);

            List<CodeAction> quickFixes = ((QuickFixProvider) diagnosticInstance).getQuickFixes(
              suitableDiagnostics,
              fixAllParams,
              documentContext
            );
            codeActions.addAll(convertCodeActionListToEitherList(quickFixes));
          });

        });

    }

    List<Either<Command, CodeAction>> quickFixes = getQuickFixes(params, documentContext);
    codeActions.addAll(quickFixes);

    return codeActions;
  }

  private List<Either<Command, CodeAction>> getQuickFixes(CodeActionParams params, DocumentContext documentContext) {
    List<String> only = params.getContext().getOnly();
    if (only != null && !only.isEmpty() && !only.contains(CodeActionKind.QuickFix)) {
      return Collections.emptyList();
    }

    List<CodeAction> actions = new ArrayList<>();

    List<Diagnostic> diagnostics = params.getContext().getDiagnostics();
    diagnostics.forEach(diagnostic ->
      actions.addAll(getCodeActionsByDiagnostic(diagnostic, params, documentContext)));

    return convertCodeActionListToEitherList(actions);
  }

  private List<CodeAction> getCodeActionsByDiagnostic(
    Diagnostic diagnostic,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    Class<? extends BSLDiagnostic> bslDiagnosticClass = DiagnosticProvider.getBSLDiagnosticClass(diagnostic);
    if (bslDiagnosticClass == null || !QuickFixProvider.class.isAssignableFrom(bslDiagnosticClass)) {
      return Collections.emptyList();
    }

    BSLDiagnostic diagnosticInstance = diagnosticProvider.getDiagnosticInstance(bslDiagnosticClass);
    return ((QuickFixProvider) diagnosticInstance).getQuickFixes(
      Collections.singletonList(diagnostic),
      params,
      documentContext
    );

  }

  private static List<Either<Command, CodeAction>> convertCodeActionListToEitherList(List<CodeAction> actions) {
    return actions.stream().map(
      (Function<CodeAction, Either<Command, CodeAction>>) Either::forRight).collect(Collectors.toList());
  }


}
