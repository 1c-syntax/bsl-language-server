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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticObjectProvider;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class CompilationDirectiveCommonModuleTest extends AbstractDiagnosticTest<CompilationDirectiveNotAllowedDiagnostic> {
  @Autowired
  private DiagnosticObjectProvider diagnosticObjectProvider;

  CompilationDirectiveCommonModuleTest() {
    super(CompilationDirectiveNotAllowedDiagnostic.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void clientDirectiveExcludesExternalOnlyModule(boolean global) {
    var document = document("НаКлиенте", CommonModule.builder().name("Тест")
      .externalConnection(true).global(global).build());

    var diagnostics = getDiagnostics(document);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(0, 0, 10);
    assertThat(diagnosticObjectProvider.get(CompilationDirectiveNeedLessDiagnostic.class).getDiagnostics(document))
      .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"НаСервереБезКонтекста", "НаКлиентеНаСервереБезКонтекста", "НаКлиентеНаСервере",
    "AtServerNoContext", "AtClientAtServerNoContext", "AtClientAtServer"})
  void formAndCommandOnlyDirectivesDoNotDeclareCommonModuleMethods(String directive) {
    var document = document(directive, CommonModule.builder().name("Тест")
      .server(true).clientManagedApplication(true).clientOrdinaryApplication(true).externalConnection(true).build());

    assertThat(getDiagnostics(document)).hasSize(1);
    assertThat(diagnosticObjectProvider.get(CompilationDirectiveNeedLessDiagnostic.class).getDiagnostics(document))
      .isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "AtClient, true, false, false, false",
    "AtServer, false, true, false, false",
    "AtClient, false, false, true, false",
    "AtServer, false, false, true, false",
    "AtServer, false, false, false, true",
    "AtClient, true, true, false, false",
    "AtServer, true, true, false, false"
  })
  void aMethodAvailableInAnyModuleContextIsAllowed(String directive, boolean server, boolean managed,
                                                   boolean ordinary, boolean external) {
    configuration.setSupportThickClientFileMode(true);
    var document = document(directive, CommonModule.builder().name("Тест").server(server)
      .clientManagedApplication(managed).clientOrdinaryApplication(ordinary).externalConnection(external).build());

    assertThat(getDiagnostics(document)).isEmpty();
    assertThat(diagnosticObjectProvider.get(CompilationDirectiveNeedLessDiagnostic.class).getDiagnostics(document))
      .hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void clientDirectiveInServerModuleRequiresFileModeSupport(boolean enabled) {
    configuration.setSupportThickClientFileMode(enabled);
    var document = document("AtClient", CommonModule.builder().name("Тест").server(true).build());

    assertThat(getDiagnostics(document)).hasSize(enabled ? 0 : 1);
  }

  @ParameterizedTest
  @CsvSource({
    "RU, AtClient, Директива компиляции \"&AtClient\" не оставляет доступных контекстов в этом общем модуле",
    "EN, НаКлиенте, Compilation directive \"&НаКлиенте\" leaves no available compilation contexts in this common module"
  })
  void messageLanguageIsIndependentOfDirectiveSpelling(Language language, String directive, String message) {
    configuration.setLanguage(language);
    diagnosticInstance.getInfo().refresh();
    var document = document(directive, CommonModule.builder().name("Тест").externalConnection(true).build());

    assertThat(getDiagnostics(document)).singleElement()
      .satisfies(diagnostic -> assertThat(diagnostic.getMessage().getLeft()).isEqualTo(message));
  }

  @Test
  void changingFlagsRestoresTheDeclaration() {
    var document = document("AtClient", CommonModule.builder().name("Тест").externalConnection(true).build());
    assertThat(getDiagnostics(document)).hasSize(1);

    doReturn(Optional.of(CommonModule.builder().name("Тест").clientManagedApplication(true).build()))
      .when(document).getMdObject();

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void unavailableFlagsAreNotGuessed() {
    var document = spy(TestUtils.getDocumentContext("&AtClient\nProcedure Example()\nEndProcedure", context));
    doReturn(ModuleType.CommonModule).when(document).getModuleType();

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void diagnosticIsEnabledForCommonModules() {
    assertThat(diagnosticInstance.getInfo().getModules()).contains(ModuleType.CommonModule);
  }

  private DocumentContext document(String directive, CommonModule module) {
    var document = spy(TestUtils.getDocumentContext(
      "&" + directive + "\nProcedure Example()\nEndProcedure", context));
    doReturn(ModuleType.CommonModule).when(document).getModuleType();
    doReturn(Optional.of(module)).when(document).getMdObject();
    return document;
  }
}
