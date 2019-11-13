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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.bsl.languageserver.diagnostics.QuickFixProvider;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuickFixSupplier {

  private final List<Class<? extends QuickFixProvider>> quickFixClasses;
  // TODO: Рефакторинг апи квик-фиксов.
  // Нужно как-то связать, что квик-фикс исправляет диагностику с таким-то кодом.
  // Возможно через аннотацию.
  private final DiagnosticSupplier diagnosticSupplier;

  public QuickFixSupplier(DiagnosticSupplier diagnosticSupplier) {
    this.diagnosticSupplier = diagnosticSupplier;
    this.quickFixClasses = createQuickFixClasses();
  }

  public List<Class<? extends QuickFixProvider>> getQuickFixClasses() {
    return new ArrayList<>(quickFixClasses);
  }

  @SuppressWarnings("unchecked")
  public Optional<Class<? extends QuickFixProvider>> getQuickFixClass(String diagnosticCode) {
    Optional<Class<? extends BSLDiagnostic>> diagnosticClass = diagnosticSupplier.getDiagnosticClass(diagnosticCode);
    if (!diagnosticClass.isPresent()) {
      return Optional.empty();
    }

    final Class<? extends BSLDiagnostic> bslDiagnosticClass = diagnosticClass.get();
    if (!quickFixClasses.contains(bslDiagnosticClass)) {
      return Optional.empty();
    }

    Class<? extends QuickFixProvider> quickFixClass = (Class<? extends QuickFixProvider>) bslDiagnosticClass;
    return Optional.of(quickFixClass);
  }

  @SuppressWarnings("unchecked")
  public QuickFixProvider getQuickFixInstance(Class<? extends QuickFixProvider> quickFixProviderClass) {
    final Class<? extends BSLDiagnostic> diagnosticClass = (Class<? extends BSLDiagnostic>) quickFixProviderClass;
    return (QuickFixProvider) diagnosticSupplier.getDiagnosticInstance(diagnosticClass);
  }

  private static List<Class<? extends QuickFixProvider>> createQuickFixClasses() {

    Reflections quickFixReflections = new Reflections(
      new ConfigurationBuilder()
        .setUrls(
          ClasspathHelper.forPackage(
            BSLDiagnostic.class.getPackage().getName(),
            ClasspathHelper.contextClassLoader(),
            ClasspathHelper.staticClassLoader()
          )
        )
    );

    return new ArrayList<>(quickFixReflections.getSubTypesOf(QuickFixProvider.class));
  }

}
