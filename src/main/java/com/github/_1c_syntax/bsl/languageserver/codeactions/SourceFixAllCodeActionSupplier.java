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
import com.github._1c_syntax.bsl.languageserver.diagnostics.QuickFixProvider;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Поставщик code action для автоматического исправления при сохранении документа.
 * <p>
 * Срабатывает на запросы клиента с {@code only}, содержащим {@link CodeActionKind#SourceFixAll}
 * (например, при {@code editor.codeActionsOnSave: {"source.fixAll": true}}).
 * Строит исправления по всем вычисленным диагностикам документа, не требуя их наличия
 * в {@code context.diagnostics}, и выдаёт результат с kind {@code source.fixAll}.
 */
@Component
@RequiredArgsConstructor
public class SourceFixAllCodeActionSupplier implements CodeActionSupplier {

  private final QuickFixSupplier quickFixSupplier;

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {
    List<String> only = params.getContext().getOnly();
    if (only == null || !only.contains(CodeActionKind.SourceFixAll)) {
      return Collections.emptyList();
    }

    List<Diagnostic> documentDiagnostics = documentContext.getDiagnostics();
    if (documentDiagnostics.isEmpty()) {
      return Collections.emptyList();
    }

    Map<Either<String, Integer>, List<Diagnostic>> diagnosticsByCode = documentDiagnostics.stream()
      .collect(Collectors.groupingBy(Diagnostic::getCode));

    return diagnosticsByCode.entrySet().stream()
      .flatMap(entry -> getFixAllCodeActions(entry.getKey(), entry.getValue(), params, documentContext).stream())
      .collect(Collectors.toList());
  }

  private List<CodeAction> getFixAllCodeActions(
    Either<String, Integer> diagnosticCode,
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    Optional<Class<? extends QuickFixProvider>> quickFixClass = quickFixSupplier.getQuickFixClass(diagnosticCode);
    if (quickFixClass.isEmpty()) {
      return Collections.emptyList();
    }

    CodeActionContext fixAllContext = new CodeActionContext();
    fixAllContext.setDiagnostics(diagnostics);
    fixAllContext.setOnly(Collections.singletonList(CodeActionKind.QuickFix));

    CodeActionParams fixAllParams = new CodeActionParams();
    fixAllParams.setTextDocument(params.getTextDocument());
    fixAllParams.setRange(params.getRange());
    fixAllParams.setContext(fixAllContext);

    QuickFixProvider quickFixInstance = quickFixSupplier.getQuickFixInstance(quickFixClass.get());

    List<CodeAction> codeActions = quickFixInstance.getQuickFixes(diagnostics, fixAllParams, documentContext);
    codeActions.forEach(codeAction -> codeAction.setKind(CodeActionKind.SourceFixAll));

    return codeActions;
  }
}
