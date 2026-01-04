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
package com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class MinimumLSPDiagnosticLevelTest {

  private static final String PATH_TO_CONFIGURATION_FILE =
    "./src/test/resources/.bsl-language-server-minimum-lsp-diagnostic-level.json";

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private DiagnosticsConfiguration diagnosticsConfiguration;

  @Test
  void testMinimumLSPDiagnosticLevelFiltering() {
    // given - configuration with minimumLSPDiagnosticLevel set to Warning
    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);
    configuration.update(configurationFile);

    // then - verify configuration loaded correctly
    assertThat(configuration.getDiagnosticsOptions().getMinimumLSPDiagnosticLevel())
      .isEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Warning);

    // when - get diagnostics for a document
    var documentContext = TestUtils.getDocumentContext("");

    List<BSLDiagnostic> diagnostics = diagnosticsConfiguration.diagnostics(documentContext);

    // then - diagnostics with LSP severity below Warning (Information=3, Hint=4) should be filtered out
    // Only diagnostics with Error (1) or Warning (2) should be included
    diagnostics.forEach(diagnostic -> {
      var diagnosticInfo = diagnostic.getInfo();
      var lspSeverity = diagnosticInfo.getLSPSeverity();
      // LSP Severity: Error=1, Warning=2, Information=3, Hint=4
      // With minimumLSPDiagnosticLevel=Warning(2), we should only have Error(1) and Warning(2)
      assertThat(lspSeverity.getValue())
        .as("Diagnostic %s should have LSP severity Warning or higher", diagnosticInfo.getCode().getStringValue())
        .isLessThanOrEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Warning.getValue());
    });
  }

  @Test
  void testMinimumLSPDiagnosticLevelNotSet() {
    // given - default configuration without minimumLSPDiagnosticLevel
    configuration.reset();

    // then - verify minimumLSPDiagnosticLevel is not set
    assertThat(configuration.getDiagnosticsOptions().getMinimumLSPDiagnosticLevel())
      .isNull();

    // when - get diagnostics for a document
    var documentContext = TestUtils.getDocumentContext("");

    List<BSLDiagnostic> diagnostics = diagnosticsConfiguration.diagnostics(documentContext);

    // then - all enabled diagnostics should be included (no filtering by LSP severity)
    assertThat(diagnostics).isNotEmpty();

    // Verify we have diagnostics with different LSP severities
    var hasHintOrInformation = diagnostics.stream()
      .map(BSLDiagnostic::getInfo)
      .map(info -> info.getLSPSeverity())
      .anyMatch(severity -> 
        severity == org.eclipse.lsp4j.DiagnosticSeverity.Hint 
        || severity == org.eclipse.lsp4j.DiagnosticSeverity.Information
      );

    // With default configuration, we should have diagnostics with Hint or Information severity
    assertThat(hasHintOrInformation)
      .as("Should have diagnostics with Hint or Information severity when minimumLSPDiagnosticLevel is not set")
      .isTrue();
  }

  @Test
  void testMinimumLSPDiagnosticLevelError() {
    // given - configuration with minimumLSPDiagnosticLevel set to Error
    configuration.reset();
    var diagnosticsOptions = configuration.getDiagnosticsOptions();
    diagnosticsOptions.setMinimumLSPDiagnosticLevel(org.eclipse.lsp4j.DiagnosticSeverity.Error);

    // when - get diagnostics for a document
    var documentContext = TestUtils.getDocumentContext("");

    List<BSLDiagnostic> diagnostics = diagnosticsConfiguration.diagnostics(documentContext);

    // then - only diagnostics with Error severity should be included
    diagnostics.forEach(diagnostic -> {
      var diagnosticInfo = diagnostic.getInfo();
      var lspSeverity = diagnosticInfo.getLSPSeverity();
      assertThat(lspSeverity)
        .as("Diagnostic %s should have LSP severity Error", diagnosticInfo.getCode().getStringValue())
        .isEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Error);
    });
  }
}
