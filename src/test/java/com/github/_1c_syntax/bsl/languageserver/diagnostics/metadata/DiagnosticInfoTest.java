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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.diagnostics.EmptyCodeBlockDiagnostic;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;


class DiagnosticInfoTest {

  @Test
  void testParameter() {

    DiagnosticInfo diagnosticInfo = new DiagnosticInfo(EmptyCodeBlockDiagnostic.class);

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
    Assertions.assertThat(diagnosticInfo.getTags().size()).isGreaterThan(0);

    Assertions.assertThat(diagnosticInfo.getDefaultConfiguration().size()).isGreaterThan(0);


    DiagnosticParameterInfo parameter = diagnosticInfo.getParameters().get(0);
    assertThat(parameter.getDescription())
      .isEqualTo("Считать комментарий в блоке кодом");

    assertThat(parameter.getDefaultValue()).isEqualTo(false);
    assertThat(parameter.getType()).isEqualTo(Boolean.class);

    Optional<DiagnosticParameterInfo> maybeParameter = diagnosticInfo.getParameter(parameter.getName());
    assertThat(maybeParameter).isPresent();
    assertThat(maybeParameter).hasValue(parameter);

    Optional<DiagnosticParameterInfo> maybeFakeParameter = diagnosticInfo.getParameter("fakeParameterName");
    assertThat(maybeFakeParameter).isEmpty();
  }

  @Test
  void testParameterEn() {

    DiagnosticInfo diagnosticEnInfo = new DiagnosticInfo(EmptyCodeBlockDiagnostic.class, Language.EN);
    assertThat(diagnosticEnInfo.getParameters().get(0).getDescription())
      .isEqualTo("Comment as code");

  }

}