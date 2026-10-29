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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdo.Constant;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class MethodCallUnavailableConditionalTest
  extends AbstractDiagnosticTest<MethodCallUnavailableDiagnostic> {

  @Autowired
  private TypeService typeService;

  MethodCallUnavailableConditionalTest() {
    super(MethodCallUnavailableDiagnostic.class);
  }

  @ParameterizedTest
  @CsvSource({"FormModule, true", "FormModule, false", "CommandModule, true", "CommandModule, false"})
  void sameNameDeclarationsInExclusiveBranchesAreNotErrors(ModuleType moduleType, boolean clientFirst) {
    var client = """
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      """;
    var server = """
      &НаСервере
      Процедура Цель()
      КонецПроцедуры
      """;
    var source = "#Если " + (clientFirst ? "Клиент" : "Сервер") + " Тогда\n"
      + (clientFirst ? client : server) + "#Иначе\n"
      + (clientFirst ? server : client) + "#КонецЕсли\n\n"
      + """
      &НаСервере
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      """;
    var document = spy(TestUtils.getDocumentContext(source, context));
    doReturn(moduleType).when(document).getModuleType();

    var diagnostics = getDiagnostics(document);

    assertThat(diagnostics)
      .as("A server declaration exists in either order of branches")
      .isEmpty();
  }

  @ParameterizedTest
  @EnumSource(value = ModuleType.class, names = {"FormModule", "CommandModule"})
  void calleeRestrictedToThinClientIsNotAvailableToAllClients(ModuleType moduleType) {
    var document = spy(TestUtils.getDocumentContext("""
      #Если ТонкийКлиент Тогда
      &НаКлиенте
      Процедура ТолькоТонкий()
      КонецПроцедуры
      #КонецЕсли

      &НаКлиенте
      Процедура Вызывающий()
        ТолькоТонкий();
      КонецПроцедуры
      """, context));
    doReturn(moduleType).when(document).getModuleType();

    var diagnostics = getDiagnostics(document);

    assertThat(diagnostics).as("Each unavailable context has its own diagnostic")
      .allSatisfy(diagnostic -> assertThat(diagnostic.getRelatedInformation()).isNull())
      .extracting(diagnostic -> diagnostic.getMessage().getLeft())
      .containsExactly(
        "Метод \"ТолькоТонкий\" недоступен в контексте вызывающего метода (Веб-клиент)",
        "Метод \"ТолькоТонкий\" недоступен в контексте вызывающего метода (Мобильный клиент)",
        "Метод \"ТолькоТонкий\" недоступен в контексте вызывающего метода (Толстый клиент (управляемое приложение))");

    diagnosticInstance.configure(Map.of("checkWebClient", "off"));
    assertThat(getDiagnostics(document)).containsExactlyElementsOf(diagnostics.subList(1, diagnostics.size()));
  }

  @ParameterizedTest
  @CsvSource({"ObjectModule, 3", "ManagerModule, 3", "RecordSetModule, 3", "ValueManagerModule, 3",
    "RecalculationModule, 3", "ExternalConnectionModule, 1", "ManagedApplicationModule, 4",
    "OrdinaryApplicationModule, 1"})
  void checksConditionalLocalMethodsInOtherModules(ModuleType moduleType, int expected) {
    var document = spy(TestUtils.getDocumentContext("""
      #Если Сервер Тогда
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      """, context));
    doReturn(moduleType).when(document).getModuleType();
    supplyPlatformAvailability(document, moduleType);
    diagnosticInstance.configure(Map.of("checkThickClientOrdinaryApplication", "on"));
    assertThat(getDiagnostics(document)).hasSize(expected);
  }

  @ParameterizedTest
  @CsvSource({"HTTPServiceModule, 1", "WEBServiceModule, 1",
    "IntegrationServiceModule, 2", "BotModule, 2", "WEBSocketClientModule, 2"})
  void serviceModulesCheckDeclaredThickClientContext(ModuleType moduleType, int expected) {
    var document = spy(TestUtils.getDocumentContext("""
      #Если Сервер Тогда
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      """, context));
    doReturn(moduleType).when(document).getModuleType();
    supplyPlatformAvailability(document, moduleType);
    diagnosticInstance.configure(Map.of("checkThickClientManagedApplication", "on"));
    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(expected);
    assertThat(diagnostics.getFirst().getMessage().getLeft())
      .isEqualTo("Метод \"Цель\" недоступен в контексте вызывающего метода (Толстый клиент (управляемое приложение))");

    diagnosticInstance.configure(Map.of("checkThickClientManagedApplication", "off",
      "checkThickClientOrdinaryApplication", "off", "checkExternalConnection", "off"));
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void unknownDeclarationConditionDoesNotProduceAnError() {
    var document = spy(TestUtils.getDocumentContext("""
      #Если Windows Тогда
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      &НаСервере
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void globalMethodCanHaveDifferentOwnersInDifferentContexts() {
    initServerContext("src/test/resources/metadata/ambiguousGlobalCalls");
    var document = spy(TestUtils.getDocumentContext("""
      &НаКлиенте
      Процедура Клиентская()
        ПерекрывающаяЦель();
      КонецПроцедуры
      &НаСервере
      Процедура Серверная()
        ПерекрывающаяЦель();
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void ordinaryApplicationExportCanProvideTheClientImplementation() {
    diagnosticInstance.configure(Map.of("checkThickClientOrdinaryApplication", "on"));
    initServerContext("src/test/resources/metadata/ambiguousGlobalCalls");
    var document = spy(TestUtils.getDocumentContext("""
      Процедура Вызывающий()
        ПерекрывающаяЦель();
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.OrdinaryApplicationModule).when(document).getModuleType();
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"auto, false, 0", "on, false, 1", "off, false, 0",
    "auto, true, 0", "on, true, 1", "off, true, 0"})
  void standaloneServerIsCheckedOnlyWhenExplicitlyEnabled(String mode, boolean hasConfiguration, int expected) {
    if (hasConfiguration) {
      initServerContext("src/test/resources/metadata/commonModuleCalls");
    }
    diagnosticInstance.configure(Map.of("checkMobileStandaloneServer", mode));
    var document = spy(TestUtils.getDocumentContext("""
      #Если НЕ МобильныйАвтономныйСервер Тогда
      &НаСервере
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      &НаСервере
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      """, context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();
    assertThat(getDiagnostics(document)).hasSize(expected);
  }

  @ParameterizedTest
  @EnumSource(value = ModuleType.class, names = {"ObjectModule", "ManagerModule", "RecordSetModule",
    "ValueManagerModule", "RecalculationModule"})
  void clientBranchCannotCallServerOnlyDeclaration(ModuleType moduleType) {
    var document = spy(TestUtils.getDocumentContext("""
      #Если Сервер Тогда
      Процедура Цель()
      КонецПроцедуры
      #Иначе
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      #КонецЕсли
      """, context));
    doReturn(moduleType).when(document).getModuleType();
    supplyPlatformAvailability(document, moduleType);
    assertThat(getDiagnostics(document)).hasSize(2);
  }

  @ParameterizedTest
  @EnumSource(value = ModuleType.class, names = {"ObjectModule", "ManagerModule", "RecordSetModule",
    "ValueManagerModule", "RecalculationModule", "HTTPServiceModule", "WEBServiceModule",
    "IntegrationServiceModule", "BotModule", "WEBSocketClientModule"})
  void missingPlatformAvailabilityDoesNotReportConditionalCalls(ModuleType moduleType) {
    var document = spy(TestUtils.getDocumentContext("""
      #Если ТонкийКлиент Тогда
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      """, context));
    doReturn(moduleType).when(document).getModuleType();
    var resolver = new CompilationContextResolver(mock(TypeRegistry.class));
    useContextResolver(resolver);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  private void supplyPlatformAvailability(DocumentContext document, ModuleType moduleType) {
    var family = switch (moduleType) {
      case ObjectModule -> "ДокументОбъект";
      case ManagerModule -> "ДокументМенеджер";
      case RecordSetModule -> "РегистрСведенийНаборЗаписей";
      case ValueManagerModule -> "КонстантаМенеджерЗначения";
      case RecalculationModule -> "ПерерасчетНаборЗаписей";
      case HTTPServiceModule -> "Модуль HTTP-сервиса";
      case WEBServiceModule -> "Модуль Web-сервиса";
      case IntegrationServiceModule -> "Модуль сервиса интеграции";
      case BotModule -> "Модуль бота";
      case WEBSocketClientModule -> "Модуль WebSocket клиента";
      default -> null;
    };
    if (family == null) {
      return;
    }
    MD md = switch (moduleType) {
      case ObjectModule, ManagerModule -> Document.builder().name("Тест").build();
      case RecordSetModule -> InformationRegister.builder().name("Тест").build();
      case ValueManagerModule -> Constant.builder().name("Тест").build();
      default -> null;
    };
    doReturn(Optional.ofNullable(md)).when(document).getMdObject();
    var metadata = mock(PlatformMetadata.class);
    var availability = switch (moduleType) {
      case HTTPServiceModule, WEBServiceModule -> Set.of(Availability.SERVER, Availability.THICK_CLIENT);
      case IntegrationServiceModule -> Set.of(Availability.SERVER, Availability.THICK_CLIENT,
        Availability.EXTERNAL_CONNECTION, Availability.MOBILE_APPLICATION_SERVER, Availability.MOBILE_STANDALONE_SERVER);
      default -> Set.of(Availability.SERVER, Availability.THICK_CLIENT, Availability.EXTERNAL_CONNECTION);
    };
    when(metadata.availabilities()).thenReturn(availability);
    var registry = mock(TypeRegistry.class);
    var type = new TypeRef(TypeKind.PLATFORM, family);
    when(registry.resolve(family)).thenReturn(Optional.of(type));
    when(registry.getTypeMetadata(type, FileType.BSL)).thenReturn(metadata);
    useContextResolver(new CompilationContextResolver(registry));
  }

  private void useContextResolver(CompilationContextResolver resolver) {
    var info = diagnosticInstance.getInfo();
    diagnosticInstance = new MethodCallUnavailableDiagnostic(
      resolver, new MemberAccessChecker(
        resolver, typeService, mock(GlobalScopeProvider.class)));
    diagnosticInstance.setInfo(info);
  }

  @ParameterizedTest
  @CsvSource({
    "RU, Метод \"Цель\" недоступен в контексте вызывающего метода (Веб-клиент)",
    "EN, Method \"Цель\" is not available in the caller context (Web client)"
  })
  void webClientCannotCallThinClientOnlyDeclaration(Language language, String expectedMessage) {
    var document = spy(TestUtils.getDocumentContext("""
      #Если ТонкийКлиент Тогда
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      #Если ВебКлиент Тогда
      &НаКлиенте
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      #КонецЕсли
      """, context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();
    var previousLanguage = configuration.getLanguage();
    try {
      configuration.setLanguage(language);
      diagnosticInstance.getInfo().refresh();
      assertThat(getDiagnostics(document)).singleElement().satisfies(diagnostic -> {
        assertThat(diagnostic.getRelatedInformation()).isNull();
        assertThat(diagnostic.getMessage().getLeft()).isEqualTo(expectedMessage);
      });
    } finally {
      configuration.setLanguage(previousLanguage);
      diagnosticInstance.getInfo().refresh();
    }
  }

  @Test
  void reportsDistinctClientAndServerPartsInFileMode() {
    configuration.setSupportThickClientFileMode(true);
    var document = spy(TestUtils.getDocumentContext("""
      #Если ТонкийКлиент Тогда
      &НаКлиентеНаСервереБезКонтекста
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      #Если ТолстыйКлиентУправляемоеПриложение Тогда
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызывающий()
        Цель();
      КонецПроцедуры
      #КонецЕсли
      """, context));
    doReturn(ModuleType.FormModule).when(document).getModuleType();

    assertThat(getDiagnostics(document))
      .allSatisfy(diagnostic -> assertThat(diagnostic.getRelatedInformation()).isNull())
      .extracting(diagnostic -> diagnostic.getMessage().getLeft())
      .containsExactly(
        "Метод \"Цель\" недоступен в контексте вызывающего метода (Толстый клиент (управляемое приложение))",
        "Метод \"Цель\" недоступен в контексте вызывающего метода (Толстый клиент (управляемое приложение), "
          + "файловый режим, клиентская часть)",
        "Метод \"Цель\" недоступен в контексте вызывающего метода (Толстый клиент (управляемое приложение), "
          + "файловый режим, серверная часть)");
  }
}
