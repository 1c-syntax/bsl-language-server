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

    List<String> only = params.getContext().getOnly();
    if (only != null && !only.isEmpty() && !only.contains(CodeActionKind.QuickFix)) {
      return Collections.emptyList();
    }

    List<CodeAction> actions = new ArrayList<>();

    List<Diagnostic> diagnostics = params.getContext().getDiagnostics();
    diagnostics.forEach(diagnostic ->
      actions.addAll(getCodeActionsByDiagnostic(diagnostic, params, documentContext)));

    return actions.stream().map(
      (Function<CodeAction, Either<Command, CodeAction>>) Either::forRight).collect(Collectors.toList());
  }

  private List<CodeAction> getCodeActionsByDiagnostic(
    Diagnostic diagnostic,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    Class<? extends BSLDiagnostic> bslDiagnosticClass = DiagnosticProvider.getBSLDiagnosticClass(diagnostic);
    if (!QuickFixProvider.class.isAssignableFrom(bslDiagnosticClass)) {
      return Collections.emptyList();
    }

    BSLDiagnostic diagnosticInstance = diagnosticProvider.getDiagnosticInstance(bslDiagnosticClass);
    return ((QuickFixProvider) diagnosticInstance).getQuickFixes(diagnostic, params, documentContext);

  }



}
