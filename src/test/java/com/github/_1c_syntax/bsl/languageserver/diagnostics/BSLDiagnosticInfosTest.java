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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.configuration.BSLLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.BSLDiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.ls_core.configuration.Language;
import com.github._1c_syntax.ls_core.diagnostics.metadata.CoreDiagnosticInfo;
import com.github._1c_syntax.ls_core.diagnostics.metadata.DiagnosticParameterInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.MissingResourceException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@SpringBootTest
class BSLDiagnosticInfosTest {

  @Autowired
  private Map<String, CoreDiagnosticInfo> diagnosticInfos;

  @Autowired
  private BSLLanguageServerConfiguration configuration;

  @Test
  void testAllDiagnosticsHaveMetadataAnnotation() {
    // when
    var diagnosticClasses = diagnosticInfos.values().stream()
      .map(coreDiagnosticInfo -> (BSLDiagnosticInfo) coreDiagnosticInfo)
      .map(BSLDiagnosticInfo::getDiagnosticClass).collect(Collectors.toList());

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
    assertThatCode(() -> diagnosticInfos.values().forEach(diagnosticInfo
      -> assertThat(((BSLDiagnosticInfo) diagnosticInfo).getDescription()).isNotEmpty()))
      .doesNotThrowAnyException();
  }

  @Test
  void testAllDiagnosticsHaveTags() {
    assertThatCode(() -> diagnosticInfos.values().forEach(diagnosticInfo
      -> assertThat(
      ((BSLDiagnosticInfo) diagnosticInfo).getTags().size() > 0
        && ((BSLDiagnosticInfo) diagnosticInfo).getTags().size() <= 3)
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
    var config = spy(configuration);
    when(config.getLanguage()).thenReturn(language);

    assertThatCode(() -> diagnosticInfos.values().forEach(diagnosticInfo -> {
      boolean allParametersHaveDescription;

      try {
        var info = new BSLDiagnosticInfo(diagnosticInfo.getDiagnosticClass(), config);
        allParametersHaveDescription = info.getParameters().stream()
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
