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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.configuration.DiagnosticLanguage;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameterInfo;
import com.github._1c_syntax.mdclasses.metadata.additional.CompatibilityMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.MissingResourceException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DiagnosticSupplierTest {

  private DiagnosticSupplier diagnosticSupplier;

  @BeforeEach
  void setUp() {
    diagnosticSupplier = getDefaultDiagnosticSupplier();
  }

  @Test
  void configureNullDryRun() {
    // given
    List<BSLDiagnostic> diagnosticInstances = DiagnosticSupplier.getDiagnosticClasses().stream()
      .map(diagnosticSupplier::getDiagnosticInstance)
      .collect(Collectors.toList());

    // when
    diagnosticInstances.forEach(diagnostic -> diagnostic.configure(null));

    // then
    // should run without runtime errors
  }


  @Test
  void testAllDiagnosticsHaveMetadataAnnotation() {
    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticSupplier.getDiagnosticClasses();

    // then
    assertThat(diagnosticClasses)
      .allMatch((Class<? extends BSLDiagnostic> diagnosticClass) ->
        diagnosticClass.isAnnotationPresent(DiagnosticMetadata.class)
      );
  }

  @Test
  void testAddDiagnosticsHaveDiagnosticName() {
    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticSupplier.getDiagnosticClasses();

    // then
    assertThatCode(() -> diagnosticClasses.forEach(diagnosticClass -> {
        DiagnosticInfo info = new DiagnosticInfo(diagnosticClass);
        String diagnosticName;
        try {
          diagnosticName = info.getName();
        } catch (MissingResourceException e) {
          throw new RuntimeException(diagnosticClass.getSimpleName() + " does not have diagnosticName", e);
        }
        assertThat(diagnosticName).isNotEmpty();
      }
    )).doesNotThrowAnyException();
  }

  @Test
  void testAllDiagnosticsHaveDiagnosticMessage() {
    // when
    List<BSLDiagnostic> diagnosticInstances = DiagnosticSupplier.getDiagnosticClasses().stream()
      .map(diagnosticSupplier::getDiagnosticInstance)
      .collect(Collectors.toList());

    // then
    assertThatCode(() -> diagnosticInstances.forEach(diagnostic -> {
        String diagnosticMessage;
        try {
          diagnosticMessage = diagnostic.getInfo().getMessage();
        } catch (MissingResourceException e) {
          throw new RuntimeException(diagnostic.getClass().getSimpleName() + " does not have diagnosticMessage", e);
        }
        assertThat(diagnosticMessage).isNotEmpty();
      }
    )).doesNotThrowAnyException();
  }

  @Test
  void testAllDiagnosticsHaveDescriptionResource() {

    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticSupplier.getDiagnosticClasses();

    // then
    assertThatCode(() -> diagnosticClasses.forEach(diagnosticClass -> {
        DiagnosticInfo info = new DiagnosticInfo(diagnosticClass);
        String diagnosticDescription;
        try {
          diagnosticDescription = info.getDescription();
        } catch (MissingResourceException e) {
          throw new RuntimeException(diagnosticClass.getSimpleName() + " does not have diagnostic description file", e);
        }
        assertThat(diagnosticDescription).isNotEmpty();
      }
    )).doesNotThrowAnyException();
  }

  @Test
  void testAllDiagnosticsHaveTags() {
    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticSupplier.getDiagnosticClasses();

    // then
    assertThat(diagnosticClasses)
      .allMatch((Class<? extends BSLDiagnostic> diagnosticClass) -> {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(diagnosticClass);
        return diagnosticInfo.getTags().size() > 0
          && diagnosticInfo.getTags().size() <= 3;
      });
  }

  @Test
  void testCompatibilityMode() {

    assertThat(
      diagnosticSupplier.getDiagnosticInstances(FileType.BSL, new CompatibilityMode(3, 10))
        .stream()
        .anyMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic)
    ).isTrue();

    assertThat(
      diagnosticSupplier.getDiagnosticInstances(FileType.BSL, new CompatibilityMode(3, 6))
        .stream()
        .anyMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic)
    ).isTrue();

    assertThat(
      diagnosticSupplier.getDiagnosticInstances(FileType.BSL, null)
        .stream()
        .anyMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic)
    ).isFalse();

    assertThat(
      diagnosticSupplier.getDiagnosticInstances(FileType.BSL, new CompatibilityMode(2, 16))
        .stream()
        .anyMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic)
    ).isFalse();

  }

  @Test
  void TestAllParametersHaveResourcesRU() {
    allParametersHaveResources(DiagnosticLanguage.RU);
  }

  @Test
  void TestAllParametersHaveResourcesEN() {
    allParametersHaveResources(DiagnosticLanguage.EN);
  }

  void allParametersHaveResources(DiagnosticLanguage languag) {

    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticSupplier.getDiagnosticClasses();

    // then
    assertThatCode(() -> diagnosticClasses.forEach(diagnosticClass -> {
        DiagnosticInfo info = new DiagnosticInfo(diagnosticClass, languag);
        Boolean allParametersHaveDescription;
        try {
          allParametersHaveDescription = info.getParameters().stream()
            .map(DiagnosticParameterInfo::getDescription)
            .noneMatch(String::isEmpty);
        } catch (MissingResourceException e) {
          throw new RuntimeException(diagnosticClass.getSimpleName() + " does not have parameters description in resources", e);
        }
        assertThat(allParametersHaveDescription).isTrue();
      }
    )).doesNotThrowAnyException();

  }

  private DiagnosticSupplier getDefaultDiagnosticSupplier() {
    return new DiagnosticSupplier(LanguageServerConfiguration.create());
  }

}