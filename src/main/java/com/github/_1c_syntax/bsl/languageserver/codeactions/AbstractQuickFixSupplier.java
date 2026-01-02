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
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Абстрактный поставщик быстрых исправлений.
 * <p>
 * Базовый класс для реализации поставщиков quick fix,
 * предоставляющий общую функциональность для создания code actions.
 */
@RequiredArgsConstructor
public abstract class AbstractQuickFixSupplier implements CodeActionSupplier {

  protected final QuickFixSupplier quickFixSupplier;

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {
    List<String> only = params.getContext().getOnly();
    if (only != null && !only.isEmpty() && !only.contains(CodeActionKind.QuickFix)) {
      return Collections.emptyList();
    }

    List<Diagnostic> incomingDiagnostics = params.getContext().getDiagnostics();
    if (incomingDiagnostics.isEmpty()) {
      return Collections.emptyList();
    }

    Set<LightDiagnostic> computedDiagnostics = documentContext.getComputedDiagnostics()
      .stream()
      .map(LightDiagnostic::new)
      .collect(Collectors.toSet());

    Stream<Diagnostic> diagnosticStream = incomingDiagnostics.stream()
      .filter(diagnostic -> computedDiagnostics.contains(new LightDiagnostic(diagnostic)));

    return processDiagnosticStream(diagnosticStream, params, documentContext)
      .collect(Collectors.toList());

  }

  protected abstract Stream<CodeAction> processDiagnosticStream(
    Stream<Diagnostic> diagnosticStream,
    CodeActionParams params,
    DocumentContext documentContext
  );

  @Value
  private static class LightDiagnostic {
    Either<String, Integer> code;
    Range range;
    String source;

    public LightDiagnostic(Diagnostic diagnostic) {
      this.code = diagnostic.getCode();
      this.range = diagnostic.getRange();
      this.source = diagnostic.getSource();
    }
  }
}
