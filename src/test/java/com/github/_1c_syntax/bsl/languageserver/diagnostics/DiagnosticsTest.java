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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Mode;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.SkipSupport;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticsConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.mdclasses.metadata.SupportConfiguration;
import com.github._1c_syntax.mdclasses.metadata.additional.CompatibilityMode;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.mdclasses.metadata.additional.SupportVariant;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@SpringBootTest
class DiagnosticsTest {

  @Autowired
  private LanguageServerConfiguration configuration;
  @Autowired
  protected ServerContext context;
  @Autowired
  protected DiagnosticsConfiguration diagnosticsConfiguration;

  private DocumentContext documentContext;

  @BeforeEach
  void createDocumentContext() {
    documentContext = TestUtils.getDocumentContext("");
  }

  @Test
  void testCompatibilityMode() {
    // given
    documentContext = spy(TestUtils.getDocumentContext(""));
    var serverContext = spy(context);
    var bslConfiguration = spy(serverContext.getConfiguration());

    doReturn(serverContext).when(documentContext).getServerContext();
    doReturn(bslConfiguration).when(serverContext).getConfiguration();

    configuration.getDiagnosticsOptions().setMode(Mode.ON);

    // when-then pairs
    doReturn(new CompatibilityMode(3, 10)).when(bslConfiguration).getCompatibilityMode();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic);

    doReturn(new CompatibilityMode(3, 6)).when(bslConfiguration).getCompatibilityMode();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic);

    doReturn(new CompatibilityMode(2, 16)).when(bslConfiguration).getCompatibilityMode();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .noneMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic);
  }

  @Test
  void testModuleType() {
    // given
    documentContext = spy(TestUtils.getDocumentContext(""));

    // when-then pairs
    doReturn(ModuleType.CommandModule).when(documentContext).getModuleType();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof CompilationDirectiveLostDiagnostic);

    doReturn(ModuleType.FormModule).when(documentContext).getModuleType();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof CompilationDirectiveLostDiagnostic);

    doReturn(ModuleType.CommonModule).when(documentContext).getModuleType();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .noneMatch(diagnostic -> diagnostic instanceof CompilationDirectiveLostDiagnostic);

    doReturn(ModuleType.UNKNOWN).when(documentContext).getModuleType();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .noneMatch(diagnostic -> diagnostic instanceof CompilationDirectiveLostDiagnostic);
  }

  @Test
  void testAllScope() {
    // given
    documentContext = spy(TestUtils.getDocumentContext(""));

    // when-then pairs
    doReturn(ModuleType.CommonModule).when(documentContext).getModuleType();
    doReturn(FileType.BSL).when(documentContext).getFileType();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof UnusedLocalMethodDiagnostic);

    doReturn(ModuleType.UNKNOWN).when(documentContext).getModuleType();
    doReturn(FileType.BSL).when(documentContext).getFileType();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .noneMatch(diagnostic -> diagnostic instanceof UnusedLocalMethodDiagnostic);

    doReturn(ModuleType.UNKNOWN).when(documentContext).getModuleType();
    doReturn(FileType.OS).when(documentContext).getFileType();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof UnusedLocalMethodDiagnostic);
  }

  @Test
  void testSkipSupport() {

    // given
    documentContext = spy(TestUtils.getDocumentContext("А = 0"));
    var supportConfiguration = mock(SupportConfiguration.class);

    // when-then pairs ComputeDiagnosticsSkipSupport.NEVER
    configuration.getDiagnosticsOptions().setSkipSupport(SkipSupport.NEVER);
    doReturn(Collections.emptyMap()).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NONE)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NOT_SUPPORTED)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.EDITABLE_SUPPORT_ENABLED)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NOT_EDITABLE)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    // when-then pairs ComputeDiagnosticsSkipSupport.WITHSUPPORTLOCKED
    configuration.getDiagnosticsOptions().setSkipSupport(SkipSupport.WITH_SUPPORT_LOCKED);
    doReturn(Collections.emptyMap()).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NONE)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NOT_SUPPORTED)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.EDITABLE_SUPPORT_ENABLED)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NOT_EDITABLE)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isEmpty();

    // when-then pairs ComputeDiagnosticsSkipSupport.WITHSUPPORT
    configuration.getDiagnosticsOptions().setSkipSupport(SkipSupport.WITH_SUPPORT);
    doReturn(Collections.emptyMap()).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NONE)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isNotEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NOT_SUPPORTED)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.EDITABLE_SUPPORT_ENABLED)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isEmpty();

    doReturn(Map.of(supportConfiguration, SupportVariant.NOT_EDITABLE)).when(documentContext).getSupportVariants();
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .isEmpty();
  }

  @Test
  void testDiagnosticModeOff() {

    // when
    configuration.getDiagnosticsOptions().setMode(Mode.OFF);

    assertThat(diagnosticsConfiguration.diagnostics(documentContext)).isEmpty();
  }

  @Test
  void testDiagnosticModeOn() {

    // when
    configuration.getDiagnosticsOptions().setMode(Mode.ON);

    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .hasSizeGreaterThan(10)
      .flatExtracting(Object::getClass)
      .doesNotContain(TooManyReturnsDiagnostic.class);
  }

  @Test
  void testDiagnosticModeAll() {

    // when
    configuration.getDiagnosticsOptions().setMode(Mode.ALL);

    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .hasSizeGreaterThan(10)
      .flatExtracting(Object::getClass)
      .contains(TooManyReturnsDiagnostic.class);
  }

  @Test
  void testDiagnosticModeOnly() {

    // when
    configuration.getDiagnosticsOptions().setMode(Mode.ONLY);
    Map<String, Either<Boolean, Map<String, Object>>> rules = new HashMap<>();
    rules.put("Typo", Either.forLeft(false));
    rules.put("TooManyReturns", Either.forLeft(true));

    configuration.getDiagnosticsOptions().setParameters(rules);

    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .hasSize(1)
      .flatExtracting(Object::getClass)
      .doesNotContain(TypoDiagnostic.class)
      .contains(TooManyReturnsDiagnostic.class)
    ;
  }

  @Test
  void testDiagnosticModeExcept() {

    // when
    configuration.getDiagnosticsOptions().setMode(Mode.EXCEPT);
    Map<String, Either<Boolean, Map<String, Object>>> rules = new HashMap<>();
    rules.put("Typo", Either.forLeft(false));
    rules.put("TooManyReturns", Either.forLeft(true));

    configuration.getDiagnosticsOptions().setParameters(rules);
    assertThat(diagnosticsConfiguration.diagnostics(documentContext))
      .hasSizeGreaterThan(10)
      .flatExtracting(BSLDiagnostic::getClass)
      .doesNotContain(TypoDiagnostic.class)
      .doesNotContain(TooManyReturnsDiagnostic.class)
      .contains(TernaryOperatorUsageDiagnostic.class)
      .contains(EmptyRegionDiagnostic.class)
    ;
  }

}
