/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.QuickFixProvider;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticObjectProvider;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QuickFixSupplier {

  private final Map<String, DiagnosticInfo> diagnosticInfos;
  private final DiagnosticObjectProvider diagnosticObjectProvider;

  // TODO: Рефакторинг апи квик-фиксов.
  // Нужно как-то связать, что квик-фикс исправляет диагностику с таким-то кодом.
  // Возможно через аннотацию.

  @SuppressWarnings("unchecked")
  public <T extends Either<String, Integer>> Optional<Class<? extends QuickFixProvider>> getQuickFixClass(
    T diagnosticCode
  ) {
    return Optional.ofNullable(
        diagnosticInfos.get(DiagnosticCode.getStringValue(diagnosticCode))
      )
      .map(DiagnosticInfo::getDiagnosticClass)
      .filter(QuickFixProvider.class::isAssignableFrom)
      .map(aClass -> (Class<? extends QuickFixProvider>) aClass);
  }

  @SuppressWarnings("unchecked")
  public QuickFixProvider getQuickFixInstance(Class<? extends QuickFixProvider> quickFixProviderClass) {
    final Class<? extends BSLDiagnostic> diagnosticClass = (Class<? extends BSLDiagnostic>) quickFixProviderClass;
    return (QuickFixProvider) diagnosticObjectProvider.get(diagnosticClass);
  }

}
