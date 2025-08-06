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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameterInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class DiagnosticInfosTest {

  @Autowired
  private Map<String, DiagnosticInfo> diagnosticInfos;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Test
  void testAllDiagnosticsHaveMetadataAnnotation() {
    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = diagnosticInfos.values().stream()
      .map(DiagnosticInfo::getDiagnosticClass).collect(Collectors.toList());

    // then
    assertThat(diagnosticClasses)
      .isNotEmpty()
      .allMatch(diagnosticClass -> diagnosticClass.isAnnotationPresent(DiagnosticMetadata.class));
  }

  @Test
  void testAddDiagnosticsHaveDiagnosticName() {
    assertThatCode(() -> diagnosticInfos.values().forEach(diagnosticInfo
      -> assertThat(diagnosticInfo.getName()).isNotEmpty()))
      .doesNotThrowAnyException();
  }

  @Test
  void testAllDiagnosticsHaveDiagnosticMessage() {
    assertThatCode(() -> diagnosticInfos.values().forEach(diagnosticInfo
      -> assertThat(diagnosticInfo.getMessage()).isNotEmpty()))
      .doesNotThrowAnyException();
  }

  @Test
  void testAllDiagnosticsHaveDescriptionResource() {
    assertThat(diagnosticInfos).allSatisfy((key, diagnosticInfo) -> {
      assertThat(diagnosticInfo.getDescription()).isNotEmpty();
    });
  }

  @Test
  void testAllDiagnosticsHaveTags() {
    assertThatCode(() -> diagnosticInfos.values().forEach(diagnosticInfo
      -> assertThat(
      !diagnosticInfo.getTags().isEmpty()
        && diagnosticInfo.getTags().size() <= 3)
      .isTrue()))
      .doesNotThrowAnyException();
  }


  @Test
  void TestAllParametersHaveResourcesEN() {
    allParametersHaveResources(Language.EN);
  }

  @Test
  void TestAllParametersHaveResourcesRU() {
    allParametersHaveResources(Language.RU);
  }

  void allParametersHaveResources(Language language) {
    configuration.setLanguage(language);

    assertThatCode(() -> diagnosticInfos.values().forEach(diagnosticInfo -> {
      boolean allParametersHaveDescription;

      try {
        allParametersHaveDescription = diagnosticInfo.getParameters().stream()
          .map(DiagnosticParameterInfo::getDescription)
          .noneMatch(String::isEmpty);
      } catch (MissingResourceException e) {
        throw new RuntimeException(diagnosticInfo.getDiagnosticClass().getSimpleName()
          + " does not have parameters description in resources", e);
      }
      assertThat(allParametersHaveDescription).isTrue();
    })).doesNotThrowAnyException();
  }
}
