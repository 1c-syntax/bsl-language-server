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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.QuickFixProvider;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Поставщик code action для исправления всех однотипных замечаний.
 * <p>
 * Создает code action, применяющую быстрое исправление
 * ко всем вхождениям диагностики в файле.
 */
@Component
public class FixAllCodeActionSupplier extends AbstractQuickFixSupplier {

  private static final int ADD_FIX_ALL_DIAGNOSTICS_THRESHOLD = 2;

  public FixAllCodeActionSupplier(QuickFixSupplier quickFixSupplier) {
    super(quickFixSupplier);
  }

  @Override
  protected Stream<CodeAction> processDiagnosticStream(
    Stream<Diagnostic> diagnosticStream,
    CodeActionParams params,
    DocumentContext documentContext
  ) {
    return diagnosticStream
      .map(Diagnostic::getCode)
      .distinct()
      .flatMap(diagnosticCode -> getFixAllCodeAction(diagnosticCode, params, documentContext).stream());
  }

  private List<CodeAction> getFixAllCodeAction(
    Either<String, Integer> diagnosticCode,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    Optional<Class<? extends QuickFixProvider>> quickFixClass = quickFixSupplier.getQuickFixClass(diagnosticCode);

    if (quickFixClass.isEmpty()) {
      return Collections.emptyList();
    }

    List<Diagnostic> suitableDiagnostics = documentContext.getComputedDiagnostics().stream()
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

    Class<? extends QuickFixProvider> quickFixProviderClass = quickFixClass.get();
    QuickFixProvider quickFixInstance = quickFixSupplier.getQuickFixInstance(quickFixProviderClass);

    return quickFixInstance.getQuickFixes(
      suitableDiagnostics,
      fixAllParams,
      documentContext
    );

  }
}
