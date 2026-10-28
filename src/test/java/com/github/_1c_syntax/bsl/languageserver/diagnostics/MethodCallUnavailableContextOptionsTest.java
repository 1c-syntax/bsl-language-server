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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdclasses.Solution;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.support.ApplicationRunMode;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class MethodCallUnavailableContextOptionsTest extends AbstractDiagnosticTest<MethodCallUnavailableDiagnostic> {
  MethodCallUnavailableContextOptionsTest() {
    super(MethodCallUnavailableDiagnostic.class);
  }

  @Test
  void allTenContextsUseAutoByDefault() {
    assertThat(diagnosticInstance.getInfo().getDefaultConfiguration()).hasSize(10)
      .allSatisfy((key, value) -> assertThat(value).isEqualTo("auto"));
  }

  @ParameterizedTest
  @CsvSource({
    "checkServer, Сервер И НЕ Клиент И НЕ ВнешнееСоединение И НЕ МобильноеПриложениеСервер И НЕ МобильныйАвтономныйСервер, 1",
    "checkThinClient, ТонкийКлиент, 1",
    "checkWebClient, ВебКлиент, 1",
    "checkMobileClient, МобильныйКлиент, 1",
    "checkThickClientManagedApplication, ТолстыйКлиентУправляемоеПриложение, 1",
    "checkThickClientOrdinaryApplication, ТолстыйКлиентОбычноеПриложение, 1",
    "checkExternalConnection, ВнешнееСоединение, 2",
    "checkMobileApplicationClient, МобильноеПриложениеКлиент, 1",
    "checkMobileApplicationServer, МобильноеПриложениеСервер, 1",
    "checkMobileStandaloneServer, МобильныйАвтономныйСервер, 1"
  })
  void disabledContextIsExcludedAfterPreprocessorSelection(String parameter, String condition, int expected) {
    var source = "#Если НЕ (" + condition + ") Тогда\n"
      + "Процедура Цель()\nКонецПроцедуры\n#Иначе\n"
      + "Процедура Вызов()\nЦель();\nКонецПроцедуры\n#КонецЕсли\n";
    var document = spy(TestUtils.getDocumentContext(source, context));
    var module = mock(CommonModule.class);
    when(module.getName()).thenReturn("ТестовыйМодуль");
    when(module.isServer()).thenReturn(true);
    when(module.isClientManagedApplication()).thenReturn(true);
    when(module.isClientOrdinaryApplication()).thenReturn(true);
    when(module.isExternalConnection()).thenReturn(true);
    doReturn(ModuleType.CommonModule).when(document).getModuleType();
    doReturn(Optional.of(module)).when(document).getMdObject();

    diagnosticInstance.configure(Map.of(parameter, "on"));
    assertThat(getDiagnostics(document)).hasSize(expected);
    diagnosticInstance.configure(Map.of(parameter, "off"));
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void disablingServerChecksDoesNotRemoveValidClientToServerCalls() {
    diagnosticInstance.configure(Map.of("checkServer", "off"));
    var document = spy(TestUtils.getDocumentContext("""
      &НаКлиенте
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      &НаСервереБезКонтекста
      Процедура Цель()
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void disablingExternalConnectionAppliesToExternalConnectionModule() {
    var document = spy(TestUtils.getDocumentContext("""
      #Если Сервер Тогда
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.ExternalConnectionModule).when(document).getModuleType();
    assertThat(getDiagnostics(document)).hasSize(1);
    diagnosticInstance.configure(Map.of("checkExternalConnection", "off"));
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void allContextsCanBeDisabled() {
    var options = new HashMap<>(diagnosticInstance.getInfo().getDefaultConfiguration());
    options.replaceAll((name, value) -> "off");
    diagnosticInstance.configure(options);
    var document = spy(getDocumentContext("MethodCallUnavailableDiagnostic"));
    doReturn(ModuleType.FormModule).when(document).getModuleType();
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"auto, 0", "on, 1", "off, 0", "ON, 1", "OFF, 0"})
  void forcedModeOverridesRootAutoSelection(String mode, int expected) {
    var document = spy(TestUtils.getDocumentContext("""
      #Если НЕ ТолстыйКлиентОбычноеПриложение Тогда
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      #Иначе
      &НаКлиенте
      Процедура Вызывающий()
        #Если ТолстыйКлиентОбычноеПриложение Тогда
          Цель();
        #КонецЕсли
      КонецПроцедуры
      #КонецЕсли
      """, context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();
    var server = mock(ServerContext.class);
    when(server.getLanguageServerConfiguration()).thenReturn(this.configuration);
    var solution = mock(Solution.class);
    var configuration = mock(Configuration.class);
    when(configuration.getDefaultRunMode()).thenReturn(ApplicationRunMode.MANAGED_APPLICATION);
    when(configuration.isUseManagedFormInOrdinaryApplication()).thenReturn(true);
    when(solution.getMergedConfiguration()).thenReturn(configuration);
    when(server.getConfiguration()).thenReturn(solution);
    doReturn(server).when(document).getServerContext();
    diagnosticInstance.configure(Map.of("checkThickClientOrdinaryApplication", mode));
    assertThat(getDiagnostics(document)).hasSize(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"on", "off", "auto"})
  void unknownModuleTypeIsNeverGuessed(String mode) {
    var options = new HashMap<>(diagnosticInstance.getInfo().getDefaultConfiguration());
    options.replaceAll((name, value) -> mode);
    diagnosticInstance.configure(options);
    var document = spy(getDocumentContext("MethodCallUnavailableDiagnostic"));
    doReturn(ModuleType.UNKNOWN).when(document).getModuleType();
    assertThat(getDiagnostics(document)).isEmpty();
  }
}
