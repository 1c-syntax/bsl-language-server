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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.EmptyCodeBlockDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.LineLengthDiagnostic;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.StringInterner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class DiagnosticInfoOverrideTest {

  private static final String PATH_TO_CONFIGURATION_FILE = 
    "./src/test/resources/.bsl-language-server-diagnostic-overrides.json";

  @Autowired
  private LanguageServerConfiguration configuration;
  
  @Autowired
  private StringInterner stringInterner;

  @Test
  void testOverrideMinimumLSPDiagnosticLevel() {
    // given
    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);
    configuration.update(configurationFile);

    // when - Create diagnostic info for a diagnostic with INFO severity
    // INFO severity normally maps to Hint LSP severity
    var diagnosticInfo = new DiagnosticInfo(LineLengthDiagnostic.class, configuration, stringInterner);

    // then - With override to MAJOR, it should be at least Warning
    assertThat(configuration.getDiagnosticsOptions().getOverrideMinimumLSPDiagnosticLevel())
      .isEqualTo(DiagnosticSeverity.MAJOR);
    assertThat(diagnosticInfo.getLSPSeverity())
      .isEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Warning);
  }

  @Test
  void testMetadataOverrideSeverity() {
    // given
    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);
    configuration.update(configurationFile);

    // when - Create diagnostic info for EmptyCodeBlock (normally MAJOR severity)
    var diagnosticInfo = new DiagnosticInfo(EmptyCodeBlockDiagnostic.class, configuration, stringInterner);

    // then - Should be overridden to BLOCKER
    assertThat(diagnosticInfo.getSeverity()).isEqualTo(DiagnosticSeverity.BLOCKER);
  }

  @Test
  void testMetadataOverrideType() {
    // given
    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);
    configuration.update(configurationFile);

    // when - Create diagnostic info for EmptyCodeBlock (normally CODE_SMELL)
    var diagnosticInfo = new DiagnosticInfo(EmptyCodeBlockDiagnostic.class, configuration, stringInterner);

    // then - Should be overridden to ERROR, which means LSP Error severity
    assertThat(diagnosticInfo.getType()).isEqualTo(DiagnosticType.ERROR);
    assertThat(diagnosticInfo.getLSPSeverity()).isEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Error);
  }

  @Test
  void testMetadataOverrideMinutesToFix() {
    // given
    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);
    configuration.update(configurationFile);

    // when - Create diagnostic info for LineLength
    var diagnosticInfo = new DiagnosticInfo(LineLengthDiagnostic.class, configuration, stringInterner);

    // then - Should be overridden to 10
    assertThat(diagnosticInfo.getMinutesToFix()).isEqualTo(10);
  }

  @Test
  void testNoOverrideWhenNotConfigured() {
    // given - Default configuration without overrides
    configuration.reset();

    // when - Create diagnostic info for EmptyCodeBlock
    var diagnosticInfo = new DiagnosticInfo(EmptyCodeBlockDiagnostic.class, configuration, stringInterner);

    // then - Should use default values from annotation
    assertThat(diagnosticInfo.getSeverity()).isEqualTo(DiagnosticSeverity.MAJOR);
    assertThat(diagnosticInfo.getType()).isEqualTo(DiagnosticType.CODE_SMELL);
    assertThat(diagnosticInfo.getLSPSeverity()).isEqualTo(org.eclipse.lsp4j.DiagnosticSeverity.Warning);
  }
}
