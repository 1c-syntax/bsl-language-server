/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.bsl.languageserver.diagnostics.QuickFixProvider;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class QuickFixSupplier {

  private final ApplicationContext applicationContext;
  private List<Class<? extends QuickFixProvider>> quickFixClasses;
  // TODO: Рефакторинг апи квик-фиксов.
  // Нужно как-то связать, что квик-фикс исправляет диагностику с таким-то кодом.
  // Возможно через аннотацию.
  private final DiagnosticSupplier diagnosticSupplier;

  @SuppressWarnings("unchecked")
  public <T extends Either<String, Number>> Optional<Class<? extends QuickFixProvider>> getQuickFixClass(
    T diagnosticCode
  ) {
    return diagnosticSupplier.getDiagnosticInfo(diagnosticCode)
      .map(DiagnosticInfo::getDiagnosticClass)
      .filter(quickFixClasses::contains)
      .map(aClass -> (Class<? extends QuickFixProvider>) aClass);
  }

  @SuppressWarnings("unchecked")
  public QuickFixProvider getQuickFixInstance(Class<? extends QuickFixProvider> quickFixProviderClass) {
    final Class<? extends BSLDiagnostic> diagnosticClass = (Class<? extends BSLDiagnostic>) quickFixProviderClass;
    return (QuickFixProvider) diagnosticSupplier.getDiagnosticInstance(diagnosticClass);
  }

  @PostConstruct
  @SuppressWarnings("unchecked")
  // TODO: в final-поле через java-config
  private void createQuickFixClasses() {
    var beanNames = applicationContext.getBeanNamesForType(QuickFixProvider.class);
    quickFixClasses = Arrays.stream(beanNames)
      .map(applicationContext::getType)
      .filter(Objects::nonNull)
      .filter(QuickFixProvider.class::isAssignableFrom)
      .map(aClass -> (Class<? extends QuickFixProvider>) aClass)
      .collect(Collectors.toList());
  }

}
