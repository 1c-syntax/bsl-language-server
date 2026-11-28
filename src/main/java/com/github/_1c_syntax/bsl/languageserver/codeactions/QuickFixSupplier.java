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
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticInfos;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticObjectProvider;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.info.DiagnosticInfo;
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
import java.util.Optional;

/**
 * Поставщик быстрых исправлений для диагностик.
 * <p>
 * Находит по коду диагностики её {@link QuickFixProvider} и получает у него исправления — для
 * одной диагностики или сразу для всех диагностик одного кода.
 */
@Component
@RequiredArgsConstructor
public class QuickFixSupplier {

  private final DiagnosticInfos diagnosticInfos;
  private final DiagnosticObjectProvider diagnosticObjectProvider;

  // TODO: Рефакторинг апи квик-фиксов.
  // Нужно как-то связать, что квик-фикс исправляет диагностику с таким-то кодом.
  // Возможно через аннотацию.

  /**
   * Быстрые исправления одной диагностики.
   *
   * @param diagnostic      Диагностика
   * @param params          Параметры исходного запроса code action
   * @param documentContext Контекст документа
   * @return Исправления; пусто, если у диагностики нет быстрых исправлений
   */
  public List<CodeAction> getQuickFixes(
    Diagnostic diagnostic,
    CodeActionParams params,
    DocumentContext documentContext
  ) {
    return quickFixProvider(diagnostic.getCode())
      .map(provider -> provider.getQuickFixes(List.of(diagnostic), params, documentContext))
      .orElse(Collections.emptyList());
  }

  /**
   * Быстрые исправления сразу всех переданных диагностик одного кода.
   * <p>
   * Провайдеру передаётся запрос с этими диагностиками в контексте и видом {@code quickfix}.
   *
   * @param diagnosticCode  Код диагностик
   * @param diagnostics     Диагностики с кодом {@code diagnosticCode}
   * @param params          Параметры исходного запроса code action
   * @param documentContext Контекст документа
   * @return Исправления; пусто, если у диагностики нет быстрых исправлений
   */
  public List<CodeAction> getFixAllQuickFixes(
    Either<String, Integer> diagnosticCode,
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {
    return quickFixProvider(diagnosticCode)
      .map(provider -> provider.getQuickFixes(diagnostics, fixAllParams(params, diagnostics), documentContext))
      .orElse(Collections.emptyList());
  }

  private Optional<QuickFixProvider> quickFixProvider(Either<String, Integer> diagnosticCode) {
    return Optional.ofNullable(diagnosticInfos.getByCode().get(DiagnosticCode.getStringValue(diagnosticCode)))
      .map(DiagnosticInfo::getDiagnosticClass)
      .filter(QuickFixProvider.class::isAssignableFrom)
      .map(diagnosticClass -> (QuickFixProvider) diagnosticObjectProvider.get(diagnosticClass));
  }

  private static CodeActionParams fixAllParams(CodeActionParams params, List<Diagnostic> diagnostics) {
    var fixAllContext = new CodeActionContext();
    fixAllContext.setDiagnostics(diagnostics);
    fixAllContext.setOnly(Collections.singletonList(CodeActionKind.QuickFix));

    var fixAllParams = new CodeActionParams();
    fixAllParams.setTextDocument(params.getTextDocument());
    fixAllParams.setRange(params.getRange());
    fixAllParams.setContext(fixAllContext);
    return fixAllParams;
  }

}
