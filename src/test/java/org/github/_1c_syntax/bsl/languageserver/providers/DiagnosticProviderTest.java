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

import org.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.MissingResourceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DiagnosticProviderTest {

  @Test
  void configureNullDryRun() {
    // given
    DiagnosticProvider diagnosticProvider = new DiagnosticProvider();
    List<BSLDiagnostic> diagnosticInstances = diagnosticProvider.getDiagnosticInstances();

    // when
    diagnosticInstances.forEach(diagnostic -> diagnostic.configure(null));

    // then
    // should run without runtime errors
  }

  @Test
  void testAllDiagnosticsHaveMetadataAnnotation() {
    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticProvider.getDiagnosticClasses();

    // then
    assertThat(diagnosticClasses)
      .allMatch((Class<? extends BSLDiagnostic> diagnosticClass) ->
        diagnosticClass.isAnnotationPresent(DiagnosticMetadata.class)
      );
  }

  @Test
  void testAddDiagnosticsHaveDiagnosticName() {
    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticProvider.getDiagnosticClasses();

    // then
    assertThatCode(() -> diagnosticClasses.forEach(diagnosticClass -> {
        try {
          DiagnosticProvider.getDiagnosticName(diagnosticClass);
        } catch (MissingResourceException e) {
          throw new RuntimeException(diagnosticClass.getSimpleName() + " does not have diagnosticName", e);
        }
      }
    )).doesNotThrowAnyException();
  }

  @Test
  void testAllDiagnosticsHaveDiagnosticMessage() {
    // when
    DiagnosticProvider diagnosticProvider = new DiagnosticProvider();
    List<BSLDiagnostic> diagnosticInstances = diagnosticProvider.getDiagnosticInstances();

    // then
    assertThatCode(() -> diagnosticInstances.forEach(diagnostic -> {
        try {
          diagnostic.getDiagnosticMessage();
        } catch (MissingResourceException e) {
          throw new RuntimeException(diagnostic.getClass().getSimpleName() + " does not have diagnosticMessage", e);
        }
      }
    )).doesNotThrowAnyException();
  }

  @Test
  void testAllDiagnosticsHaveDescriptionResource() {

    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticProvider.getDiagnosticClasses();

    // then
    assertThat(diagnosticClasses).allMatch(diagnosticClass -> !"".equals(DiagnosticProvider.getDiagnosticDescription(diagnosticClass)));

  }

  @Test
  void testAllDiagnosticsHaveMinutesToFix()
  {

    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticProvider.getDiagnosticClasses();

    // then
    assertThat(diagnosticClasses).allMatch(diagnosticClass -> DiagnosticProvider.getMinutesToFix(diagnosticClass) != 0);

  }
}