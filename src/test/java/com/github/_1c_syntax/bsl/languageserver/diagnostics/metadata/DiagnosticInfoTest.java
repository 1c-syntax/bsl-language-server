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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DeprecatedAttributes8312Diagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.EmptyCodeBlockDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.ForbiddenMetadataNameDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.MultilingualStringHasAllDeclaredLanguagesDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.UnusedLocalMethodDiagnostic;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.StringInterner;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class DiagnosticInfoTest {

  @Autowired
  private LanguageServerConfiguration configuration;
  @Autowired
  private StringInterner stringInterner;

  @Test
  void testParameter() {

    var diagnosticInfo = new DiagnosticInfo(EmptyCodeBlockDiagnostic.class, configuration, stringInterner);

    Assertions.assertThat(diagnosticInfo.getCode()).isEqualTo(Either.forLeft("EmptyCodeBlock"));
    Assertions.assertThat(diagnosticInfo.getName()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getMessage()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getMessage("")).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getType()).isEqualTo(DiagnosticType.CODE_SMELL);
    Assertions.assertThat(diagnosticInfo.getSeverity()).isEqualTo(DiagnosticSeverity.MAJOR);
    Assertions.assertThat(diagnosticInfo.getSeverity()).isEqualTo(DiagnosticSeverity.MAJOR);
    Assertions.assertThat(diagnosticInfo.getLSPSeverity()).isEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Warning);
    Assertions.assertThat(diagnosticInfo.getCompatibilityMode()).isEqualTo(DiagnosticCompatibilityMode.UNDEFINED);
    Assertions.assertThat(diagnosticInfo.getScope()).isEqualTo(DiagnosticScope.ALL);
    Assertions.assertThat(diagnosticInfo.getMinutesToFix()).isEqualTo(5);
    Assertions.assertThat(diagnosticInfo.isActivatedByDefault()).isTrue();
    Assertions.assertThat(diagnosticInfo.getTags()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getLSPTags()).isEmpty();
    Assertions.assertThat(diagnosticInfo.canLocateOnProject()).isFalse();

    Assertions.assertThat(diagnosticInfo.getDefaultConfiguration()).isNotEmpty();

    var parameter = diagnosticInfo.getParameters().get(0);
    assertThat(parameter.getDescription())
      .isEqualTo("Считать комментарий в блоке кодом");

    assertThat(parameter.getDefaultValue()).isEqualTo(false);
    assertThat(parameter.getType()).isEqualTo(Boolean.class);

    var maybeParameter = diagnosticInfo.getParameter(parameter.getName());
    assertThat(maybeParameter)
      .isPresent()
      .hasValue(parameter);

    var maybeFakeParameter = diagnosticInfo.getParameter("fakeParameterName");
    assertThat(maybeFakeParameter).isEmpty();
  }

  @Test
  void testLSPTags() {
    // given
    var diagnosticInfo = new DiagnosticInfo(UnusedLocalMethodDiagnostic.class, configuration, stringInterner);

    // then
    assertThat(diagnosticInfo.getLSPTags()).contains(DiagnosticTag.Unnecessary);

    // given
    diagnosticInfo = new DiagnosticInfo(DeprecatedAttributes8312Diagnostic.class, configuration, stringInterner);

    // then
    assertThat(diagnosticInfo.getLSPTags()).contains(DiagnosticTag.Deprecated);
  }

  @Test
  void testParameterSuper() {

    var diagnosticInfo = new DiagnosticInfo(MultilingualStringHasAllDeclaredLanguagesDiagnostic.class, configuration, stringInterner);

    Assertions.assertThat(diagnosticInfo.getCode()).isEqualTo(Either.forLeft("MultilingualStringHasAllDeclaredLanguages"));
    Assertions.assertThat(diagnosticInfo.getName()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getMessage()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getMessage("")).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getType()).isEqualTo(DiagnosticType.ERROR);
    Assertions.assertThat(diagnosticInfo.getSeverity()).isEqualTo(DiagnosticSeverity.MINOR);
    Assertions.assertThat(diagnosticInfo.getLSPSeverity()).isEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Error);
    Assertions.assertThat(diagnosticInfo.getCompatibilityMode()).isEqualTo(DiagnosticCompatibilityMode.UNDEFINED);
    Assertions.assertThat(diagnosticInfo.getScope()).isEqualTo(DiagnosticScope.BSL);
    Assertions.assertThat(diagnosticInfo.getMinutesToFix()).isEqualTo(2);
    Assertions.assertThat(diagnosticInfo.isActivatedByDefault()).isTrue();
    Assertions.assertThat(diagnosticInfo.getTags()).isNotEmpty();

    Assertions.assertThat(diagnosticInfo.getDefaultConfiguration()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getParameters()).hasSize(1);
    Assertions.assertThat(diagnosticInfo.canLocateOnProject()).isFalse();

    var parameter = diagnosticInfo.getParameters().get(0);
    assertThat(parameter.getDescription())
      .isEqualTo("Заявленные языки");

    assertThat(parameter.getDefaultValue()).isEqualTo("ru");
    assertThat(parameter.getType()).isEqualTo(String.class);

    var maybeParameter = diagnosticInfo.getParameter(parameter.getName());
    assertThat(maybeParameter)
      .isPresent()
      .hasValue(parameter);

    var maybeFakeParameter = diagnosticInfo.getParameter("fakeParameterName");
    assertThat(maybeFakeParameter).isEmpty();
  }

  @Test
  void testCanLocateOnProject() {
    var diagnosticInfo = new DiagnosticInfo(ForbiddenMetadataNameDiagnostic.class, configuration, stringInterner);
    Assertions.assertThat(diagnosticInfo.getCode()).isEqualTo(Either.forLeft("ForbiddenMetadataName"));
    Assertions.assertThat(diagnosticInfo.getName()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getMessage()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.getType()).isEqualTo(DiagnosticType.ERROR);
    Assertions.assertThat(diagnosticInfo.getSeverity()).isEqualTo(DiagnosticSeverity.BLOCKER);
    Assertions.assertThat(diagnosticInfo.getLSPSeverity()).isEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Error);
    Assertions.assertThat(diagnosticInfo.getCompatibilityMode()).isEqualTo(DiagnosticCompatibilityMode.UNDEFINED);
    Assertions.assertThat(diagnosticInfo.getScope()).isEqualTo(DiagnosticScope.BSL);
    Assertions.assertThat(diagnosticInfo.getMinutesToFix()).isEqualTo(30);
    Assertions.assertThat(diagnosticInfo.isActivatedByDefault()).isTrue();
    Assertions.assertThat(diagnosticInfo.getTags()).isNotEmpty();
    Assertions.assertThat(diagnosticInfo.canLocateOnProject()).isTrue();
    Assertions.assertThat(diagnosticInfo.getExtraMinForComplexity()).isZero();
  }

  @Test
  void testParameterEn() {

    // given
    configuration.setLanguage(Language.EN);

    // when
    var diagnosticEnInfo = new DiagnosticInfo(EmptyCodeBlockDiagnostic.class, configuration, stringInterner);

    // then
    assertThat(diagnosticEnInfo.getParameters().get(0).getDescription())
      .isEqualTo("Comment as code");
  }
}
