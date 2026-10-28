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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/** Регрессии по результатам /CheckConfig платформы 1С 8.3.27. */
class MethodCallUnavailableNativeContextsTest extends AbstractDiagnosticTest<MethodCallUnavailableDiagnostic> {
  MethodCallUnavailableNativeContextsTest() {
    super(MethodCallUnavailableDiagnostic.class);
  }

  @Test
  void detectsUnavailableCallInBranchSpecificToFileMode() {
    configuration.setSupportThickClientFileMode(true);
    var document = commonModule("""
      #Если НЕ Сервер Тогда
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      #Если Клиент И Сервер Тогда
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      #КонецЕсли
      """);

    var diagnostics = getDiagnostics(document);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.getFirst().getMessage().getLeft()).contains("файловый режим");
    diagnosticInstance.configure(Map.of("checkThickClientManagedApplication", "off",
      "checkThickClientOrdinaryApplication", "off"));
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void serverDeclarationExistsInMergedFileContext() {
    configuration.setSupportThickClientFileMode(true);
    var document = commonModule("""
      #Если Сервер Тогда
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      #Если Клиент И Сервер Тогда
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      #КонецЕсли
      """);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void inactiveLocalDeclarationDoesNotHideAvailableGlobalMethod() {
    initServerContext("src/test/resources/metadata/ambiguousGlobalCalls");
    var document = commonModule("""
      #Если Сервер Тогда
      Процедура ПерекрывающаяЦель()
      КонецПроцедуры
      #КонецЕсли
      Процедура Вызывающий()
        ПерекрывающаяЦель();
      КонецПроцедуры
      """);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void atClientAtServerDoesNotDeclareCommonModuleMethod(boolean global) {
    var document = commonModule("""
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      &НаКлиентеНаСервере
      Процедура Цель()
      КонецПроцедуры
      """, global);

    assertThat(getDiagnostics(document)).hasSize(global ? 2 : 6);
  }

  @Test
  void globalCommonModuleDirectiveIsNotIgnored() {
    var document = commonModule("""
      Процедура Вызывающий() Экспорт
        Цель();
      КонецПроцедуры
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      """, true);

    assertThat(getDiagnostics(document)).hasSize(2);
  }

  @Test
  void externalConnectionCanCallServerCallModuleWithoutExternalFlag() {
    initServerContext("src/test/resources/metadata/commonModuleCalls");
    var document = spy(TestUtils.getDocumentContext("""
      Процедура ПроверитьRPC()
        СерверныйМодульВызовСервера.Цель();
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.ExternalConnectionModule).when(document).getModuleType();

    assertThat(getDiagnostics(document)).isEmpty();
    diagnosticInstance.configure(Map.of("checkServer", "off"));
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void externalConnectionStillRejectsServerModuleWithoutServerCall() {
    initServerContext("src/test/resources/metadata/commonModuleCalls");
    var document = spy(TestUtils.getDocumentContext("""
      Процедура ПроверитьПрямойВызов()
        СерверныйМодуль.Цель();
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.ExternalConnectionModule).when(document).getModuleType();

    assertThat(getDiagnostics(document)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"КлиентСерверныйМодуль.СервернаяФункция();",
    "Значение = КлиентСерверныйМодуль.СервернаяФункция();"})
  void directCommonModuleCallIsCheckedInStatementAndExpression(String call) {
    initServerContext("src/test/resources/metadata/commonModuleCalls");
    var document = spy(TestUtils.getDocumentContext(
      "&НаКлиенте\nПроцедура Вызов()\n" + call + "\nКонецПроцедуры\n", context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();

    assertThat(getDiagnostics(document)).hasSize(4);
  }

  @Test
  void inactiveLocalDeclarationDoesNotHidePlatformGlobal() {
    var document = spy(TestUtils.getDocumentContext("""
      #Если ТонкийКлиент Тогда
      &НаКлиенте
      Процедура Сообщить(Текст)
      КонецПроцедуры
      #КонецЕсли
      &НаСервереБезКонтекста
      Процедура Вызов()
        Сообщить("Тест");
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void sessionDoesNotAcquireClientServerThickClientContext() {
    var document = spy(TestUtils.getDocumentContext("""
      #Если Сервер Тогда
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.SessionModule).when(document).getModuleType();

    assertThat(getDiagnostics(document)).isEmpty();
  }

  private DocumentContext commonModule(String source) {
    return commonModule(source, false);
  }

  private DocumentContext commonModule(String source, boolean global) {
    var document = spy(TestUtils.getDocumentContext(source, context));
    var module = CommonModule.builder().name("Audit").server(true).clientManagedApplication(!global).global(global).build();
    doReturn(ModuleType.CommonModule).when(document).getModuleType();
    doReturn(Optional.of(module)).when(document).getMdObject();
    return document;
  }
}
