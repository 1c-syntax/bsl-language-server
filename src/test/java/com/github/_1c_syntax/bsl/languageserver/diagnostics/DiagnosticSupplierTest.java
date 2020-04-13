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
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Mode;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.SkipSupport;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameterInfo;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.mdclasses.metadata.SupportConfiguration;
import com.github._1c_syntax.mdclasses.metadata.additional.CompatibilityMode;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.mdclasses.metadata.additional.SupportVariant;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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
    // given
    var documentContext = spy(TestUtils.getDocumentContext(""));
    var serverContext = spy(documentContext.getServerContext());
    var configuration = spy(serverContext.getConfiguration());

    when(documentContext.getServerContext()).thenReturn(serverContext);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    // when-then pairs
    when(configuration.getCompatibilityMode()).thenReturn(new CompatibilityMode(3, 10));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic);

    when(configuration.getCompatibilityMode()).thenReturn(new CompatibilityMode(3, 6));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic);

    when(configuration.getCompatibilityMode()).thenReturn(new CompatibilityMode(2, 16));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .noneMatch(diagnostic -> diagnostic instanceof DeprecatedFindDiagnostic);
  }

  @Test
  void testModuleType() {
    // given
    var documentContext = spy(TestUtils.getDocumentContext(""));

    // when-then pairs
    when(documentContext.getModuleType()).thenReturn(ModuleType.CommandModule);
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof CompilationDirectiveLostDiagnostic);

    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof CompilationDirectiveLostDiagnostic);

    when(documentContext.getModuleType()).thenReturn(ModuleType.CommonModule);
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .noneMatch(diagnostic -> diagnostic instanceof CompilationDirectiveLostDiagnostic);

    when(documentContext.getModuleType()).thenReturn(ModuleType.Unknown);
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .noneMatch(diagnostic -> diagnostic instanceof CompilationDirectiveLostDiagnostic);
  }

  @Test
  void testAllScope() {
    // given
    var documentContext = spy(TestUtils.getDocumentContext(""));

    // when-then pairs
    when(documentContext.getModuleType()).thenReturn(ModuleType.CommonModule);
    when(documentContext.getFileType()).thenReturn(FileType.BSL);
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof UnusedLocalMethodDiagnostic);

    when(documentContext.getModuleType()).thenReturn(ModuleType.Unknown);
    when(documentContext.getFileType()).thenReturn(FileType.BSL);
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .noneMatch(diagnostic -> diagnostic instanceof UnusedLocalMethodDiagnostic);

    when(documentContext.getModuleType()).thenReturn(ModuleType.Unknown);
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .anyMatch(diagnostic -> diagnostic instanceof UnusedLocalMethodDiagnostic);
  }

  @Test
  void testSkipSupport() {

    // given
    var lsConfiguration = LanguageServerConfiguration.create();
    diagnosticSupplier = new DiagnosticSupplier(lsConfiguration);
    var documentContext = spy(TestUtils.getDocumentContext("А = 0"));
    var supportConfiguration = mock(SupportConfiguration.class);

    // when-then pairs ComputeDiagnosticsSkipSupport.NEVER
    lsConfiguration.getDiagnosticsOptions().setSkipSupport(SkipSupport.NEVER);
    when(documentContext.getSupportVariants()).thenReturn(Collections.emptyMap());
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NONE));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NOT_SUPPORTED));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.EDITABLE_SUPPORT_ENABLED));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NOT_EDITABLE));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    // when-then pairs ComputeDiagnosticsSkipSupport.WITHSUPPORTLOCKED
    lsConfiguration.getDiagnosticsOptions().setSkipSupport(SkipSupport.WITH_SUPPORT_LOCKED);
    when(documentContext.getSupportVariants()).thenReturn(Collections.emptyMap());
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NONE));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NOT_SUPPORTED));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.EDITABLE_SUPPORT_ENABLED));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NOT_EDITABLE));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isEmpty();

    // when-then pairs ComputeDiagnosticsSkipSupport.WITHSUPPORT
    lsConfiguration.getDiagnosticsOptions().setSkipSupport(SkipSupport.WITH_SUPPORT);
    when(documentContext.getSupportVariants()).thenReturn(Collections.emptyMap());
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NONE));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isNotEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NOT_SUPPORTED));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.EDITABLE_SUPPORT_ENABLED));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isEmpty();

    when(documentContext.getSupportVariants()).thenReturn(Map.of(supportConfiguration, SupportVariant.NOT_EDITABLE));
    assertThat(diagnosticSupplier.getDiagnosticInstances(documentContext))
      .isEmpty();
  }

  @Test
  void TestAllParametersHaveResourcesRU() {
    allParametersHaveResources(Language.RU);
  }

  @Test
  void testDiagnosticModeOff() {
    // given
    var lsConfiguration = LanguageServerConfiguration.create();
    var documentContext = TestUtils.getDocumentContext("");
    diagnosticSupplier = new DiagnosticSupplier(lsConfiguration);

    // when-then pairs
    lsConfiguration.getDiagnosticsOptions().setMode(Mode.OFF);
    List<BSLDiagnostic> diagnostics = diagnosticSupplier.getDiagnosticInstances(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testDiagnosticModeOn() {
    // given
    var lsConfiguration = LanguageServerConfiguration.create();
    var documentContext = TestUtils.getDocumentContext("");
    diagnosticSupplier = new DiagnosticSupplier(lsConfiguration);

    // when
    lsConfiguration.getDiagnosticsOptions().setMode(Mode.ON);
    List<BSLDiagnostic> diagnostics = diagnosticSupplier.getDiagnosticInstances(documentContext);

    assertThat(diagnostics)
      .hasSizeGreaterThan(10)
      .flatExtracting(Object::getClass)
      .doesNotContain(TooManyReturnsDiagnostic.class);
  }

  @Test
  void testDiagnosticModeAll() {
    // given
    var lsConfiguration = LanguageServerConfiguration.create();
    var documentContext = TestUtils.getDocumentContext("");
    diagnosticSupplier = new DiagnosticSupplier(lsConfiguration);

    // when
    lsConfiguration.getDiagnosticsOptions().setMode(Mode.ALL);
    List<BSLDiagnostic> diagnostics = diagnosticSupplier.getDiagnosticInstances(documentContext);

    assertThat(diagnostics)
      .hasSizeGreaterThan(10)
      .flatExtracting(Object::getClass)
      .contains(TooManyReturnsDiagnostic.class);
  }

  @Test
  void testDiagnosticModeOnly() {
    // given
    var lsConfiguration = LanguageServerConfiguration.create();
    var documentContext = TestUtils.getDocumentContext("");
    diagnosticSupplier = new DiagnosticSupplier(lsConfiguration);

    // when
    lsConfiguration.getDiagnosticsOptions().setMode(Mode.ONLY);
    Map<String, Either<Boolean, Map<String, Object>>> rules = new HashMap<>();
    rules.put("Typo", Either.forLeft(true));
    rules.put("TooManyReturns", Either.forLeft(true));

    lsConfiguration.getDiagnosticsOptions().setRules(rules);
    List<BSLDiagnostic> diagnostics = diagnosticSupplier.getDiagnosticInstances(documentContext);

    assertThat(diagnostics)
      .hasSize(2)
      .flatExtracting(Object::getClass)
      .contains(TypoDiagnostic.class)
      .contains(TooManyReturnsDiagnostic.class)
    ;
  }

  @Test
  void testDiagnosticModeExcept() {
    // given
    var lsConfiguration = LanguageServerConfiguration.create();
    var documentContext = TestUtils.getDocumentContext("");
    diagnosticSupplier = new DiagnosticSupplier(lsConfiguration);

    // when
    lsConfiguration.getDiagnosticsOptions().setMode(Mode.EXCEPT);
    Map<String, Either<Boolean, Map<String, Object>>> rules = new HashMap<>();
    rules.put("Typo", Either.forLeft(true));
    rules.put("TooManyReturns", Either.forLeft(true));

    lsConfiguration.getDiagnosticsOptions().setRules(rules);
    List<BSLDiagnostic> diagnostics = diagnosticSupplier.getDiagnosticInstances(documentContext);

    assertThat(diagnostics)
      .hasSizeGreaterThan(10)
      .flatExtracting(Object::getClass)
      .doesNotContain(TypoDiagnostic.class)
      .doesNotContain(TooManyReturnsDiagnostic.class)
      .contains(TernaryOperatorUsageDiagnostic.class)
      .contains(EmptyRegionDiagnostic.class)
    ;
  }

  @Test
  void TestAllParametersHaveResourcesEN() {
    allParametersHaveResources(Language.EN);
  }

  void allParametersHaveResources(Language language) {

    // when
    List<Class<? extends BSLDiagnostic>> diagnosticClasses = DiagnosticSupplier.getDiagnosticClasses();

    // then
    assertThatCode(() -> diagnosticClasses.forEach(diagnosticClass -> {
        DiagnosticInfo info = new DiagnosticInfo(diagnosticClass, language);
        boolean allParametersHaveDescription;
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