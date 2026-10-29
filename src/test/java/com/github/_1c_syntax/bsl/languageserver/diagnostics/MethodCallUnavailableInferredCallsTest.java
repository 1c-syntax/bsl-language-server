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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MethodCallUnavailableInferredCallsTest extends AbstractDiagnosticTest<MethodCallUnavailableDiagnostic> {
  @Autowired
  private TypeService typeService;

  MethodCallUnavailableInferredCallsTest() {
    super(MethodCallUnavailableDiagnostic.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Модуль.ЦельЕслиСервер();",
    "Модуль.ЦельЕслиСервер(1 + 2);",
    "Результат = Модуль.СервернаяФункция();",
    "Копия = Модуль; Копия.ЦельЕслиСервер();",
    "ОбщегоНазначения.ОбщийМодуль(\"КлиентСерверныйМодуль\").ЦельЕслиСервер();",
    "Результат = ОбщегоНазначения.ОбщийМодуль(\"КлиентСерверныйМодуль\").СервернаяФункция();"
  })
  void checksKnownModuleRegardlessOfReceiverSyntax(String call) {
    var document = document("""
      &НаКлиенте
      Процедура Вызов()
        Модуль = ОбщегоНазначения.ОбщийМодуль("КлиентСерверныйМодуль");
        %s
      КонецПроцедуры
      """.formatted(call), ModuleType.FormModule);

    assertThat(getDiagnostics(document)).hasSize(4);
    diagnosticInstance.configure(Map.of("checkThinClient", "off", "checkWebClient", "off",
      "checkMobileClient", "off", "checkThickClientManagedApplication", "off"));
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "СерверныйМодуль, СерверныйМодульВызовСервера",
    "СерверныйМодульВызовСервера, СерверныйМодуль"
  })
  void checksEveryInferredModuleInEitherOrder(String first, String second) {
    var document = alternativeModules(first, second);
    var types = typeService.receiverTypesAt(document, new Position(6, 9));
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactlyInAnyOrder(first, second);

    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.getFirst().getMessage().getLeft()).contains("СерверныйМодуль");
  }

  @ParameterizedTest
  @CsvSource({"СерверныйМодуль, КлиентскийМодуль", "КлиентскийМодуль, СерверныйМодуль"})
  void reportsWhenEveryInferredModuleIsUnavailable(String first, String second) {
    var document = alternativeModules(first, second);

    var diagnostics = getDiagnostics(document);

    assertThat(diagnostics).hasSize(2)
      .allSatisfy(diagnostic -> assertThat(diagnostic.getRelatedInformation()).isNull());
    assertThat(diagnostics.getFirst().getMessage().getLeft()).endsWith("(Внешнее соединение)");
    assertThat(diagnostics.getLast().getMessage().getLeft()).endsWith("(Внешнее соединение, файловый режим)");
  }

  @ParameterizedTest
  @ValueSource(strings = {"НеизвестныйПараметр", "Новый Структура", "Неопределено"})
  void otherAlternativeDoesNotHideUnavailableKnownModule(String alternative) {
    var document = document("""
      Процедура Вызов(Условие, НеизвестныйПараметр)
        Если Условие Тогда
          Модуль = ОбщегоНазначения.ОбщийМодуль("СерверныйМодуль");
        Иначе
          Модуль = %s;
        КонецЕсли;
        Модуль.Цель();
      КонецПроцедуры
      """.formatted(alternative), ModuleType.ExternalConnectionModule);

    assertThat(getDiagnostics(document)).hasSize(1);
  }

  @Test
  void unknownReceiverIsSkipped() {
    var document = document("""
      Процедура Вызов(Модуль)
        Модуль.Цель();
      КонецПроцедуры
      """, ModuleType.ExternalConnectionModule);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void arbitraryReceiverIsSkipped() {
    var document = document("""
      // Параметры:
      //  Модуль - Произвольный
      Процедура Вызов(Модуль)
        Модуль.Цель();
      КонецПроцедуры
      """, ModuleType.ExternalConnectionModule);
    assertThat(typeService.receiverTypesAt(document, new Position(3, 9)).refs()).containsExactly(TypeRef.ANY);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void arbitraryAlternativeDoesNotHideUnavailableKnownModule() {
    var document = document("""
      // Параметры:
      //  Условие - Булево
      //  ИнаяЦель - Произвольный
      Процедура Вызов(Условие, ИнаяЦель)
        Если Условие Тогда
          Модуль = ОбщегоНазначения.ОбщийМодуль("СерверныйМодуль");
        Иначе
          Модуль = ИнаяЦель;
        КонецЕсли;
        Модуль.Цель();
      КонецПроцедуры
      """, ModuleType.ExternalConnectionModule);
    assertThat(typeService.receiverTypesAt(document, new Position(9, 9)).refs())
      .contains(TypeRef.ANY).extracting(TypeRef::qualifiedName).contains("СерверныйМодуль");

    assertThat(getDiagnostics(document)).hasSize(1);
  }

  @Test
  void missingDeclarationForOneAlternativeDoesNotHideUnavailableMethod() {
    var document = document("""
      &НаКлиенте
      Процедура Вызов(Условие)
        Если Условие Тогда
          Модуль = ОбщегоНазначения.ОбщийМодуль("КлиентСерверныйМодуль");
        Иначе
          Модуль = ОбщегоНазначения.ОбщийМодуль("КлиентскийМодуль");
        КонецЕсли;
        Модуль.ЦельЕслиСервер();
      КонецПроцедуры
      """, ModuleType.FormModule);

    assertThat(getDiagnostics(document)).hasSize(4);
  }

  @Test
  void preprocessorExcludesCallInUnavailableContext() {
    var document = document("""
      &НаКлиенте
      Процедура Вызов()
        Модуль = ОбщегоНазначения.ОбщийМодуль("КлиентСерверныйМодуль");
      #Если Сервер Тогда
        Модуль.ЦельЕслиСервер();
      #КонецЕсли
      КонецПроцедуры
      """, ModuleType.FormModule);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void preprocessorAssignmentsSelectContextSpecificTargets() {
    var document = document("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов()
      #Если Сервер Тогда
        Модуль = СерверныйМодуль;
      #Иначе
        Модуль = КлиентскийМодуль;
      #КонецЕсли
        Модуль.Цель();
      КонецПроцедуры
      """, ModuleType.FormModule);
    var types = typeService.receiverTypesAt(document, new Position(7, 9));
    assertThat(types.refs()).extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("СерверныйМодуль", "КлиентскийМодуль");

    var diagnostics = getDiagnostics(document);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void runtimeConditionDoesNotSuppressKnownTarget() {
    var document = document("""
      &НаКлиенте
      Процедура Вызов(Выполнять)
        Модуль = ОбщегоНазначения.ОбщийМодуль("КлиентСерверныйМодуль");
        Если Выполнять Тогда
          Модуль.ЦельЕслиСервер();
        КонецЕсли;
      КонецПроцедуры
      """, ModuleType.FormModule);

    assertThat(getDiagnostics(document)).hasSize(4);
  }

  @Test
  void checksResolvedFormMethodAsWellAsCommonModules() {
    var document = document("""
      &НаСервере
      Процедура Вызов()
        ЭтотОбъект.Клиентская();
      КонецПроцедуры
      &НаКлиенте
      Процедура Клиентская() Экспорт
      КонецПроцедуры
      """, ModuleType.FormModule);

    assertThat(getDiagnostics(document)).hasSize(1);
  }

  @Test
  void clientCommonModuleCanCallFormMethodsInFileMode() {
    configuration.setSupportThickClientFileMode(true);
    document("""
      &НаКлиенте
      Процедура Клиентская() Экспорт
      КонецПроцедуры
      &НаСервере
      Процедура Серверная() Экспорт
      КонецПроцедуры
      """, ModuleType.FormModule);
    var uri = Path.of("src/test/resources/metadata/commonModuleCalls/CommonModules/ОбщегоНазначения/Ext/Module.bsl").toUri();
    var caller = TestUtils.getDocumentContext(uri, """
      Процедура Вызов()
        Форма = ПолучитьФорму("ОбщаяФорма.Тест");
        Форма.Клиентская();
        Форма.Серверная();
      КонецПроцедуры
      """, context);

    diagnosticInstance.configure(Map.of("checkServer", "off", "checkExternalConnection", "off",
      "checkThickClientOrdinaryApplication", "on"));
    var diagnostics = getDiagnostics(caller);

    assertThat(diagnostics).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"СерверныйМодуль, КлиентскийМодуль", "КлиентскийМодуль, СерверныйМодуль"})
  void reportsDifferentUnavailableTargetsInEveryContext(String first, String second) {
    var document = document("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов(Условие)
        Если Условие Тогда
          Модуль = ОбщегоНазначения.ОбщийМодуль("%s");
        Иначе
          Модуль = ОбщегоНазначения.ОбщийМодуль("%s");
        КонецЕсли;
        Модуль.Цель();
      КонецПроцедуры
      """.formatted(first, second), ModuleType.FormModule);
    var options = new HashMap<>(diagnosticInstance.getInfo().getDefaultConfiguration());
    options.replaceAll((name, value) -> "off");
    options.put("checkServer", "on");
    options.put("checkThinClient", "on");
    diagnosticInstance.configure(options);

    assertThat(getDiagnostics(document))
      .allSatisfy(diagnostic -> assertThat(diagnostic.getRelatedInformation()).isNull())
      .extracting(diagnostic -> diagnostic.getMessage().getLeft())
      .containsExactlyInAnyOrder(
        "Общий модуль \"КлиентскийМодуль\" недоступен в контексте вызова (Сервер)",
        "Общий модуль \"СерверныйМодуль\" недоступен в контексте вызова (Тонкий клиент)");
  }

  private DocumentContext alternativeModules(String first, String second) {
    return document("""
      Процедура Вызов(Условие)
        Если Условие Тогда
          Модуль = ОбщегоНазначения.ОбщийМодуль("%s");
        Иначе
          Модуль = ОбщегоНазначения.ОбщийМодуль("%s");
        КонецЕсли;
        Модуль.Цель();
      КонецПроцедуры
      """.formatted(first, second), ModuleType.ExternalConnectionModule);
  }

  private DocumentContext document(String source, ModuleType moduleType) {
    initServerContext("src/test/resources/metadata/commonModuleCalls");
    var relative = moduleType == ModuleType.FormModule
      ? "CommonForms/Тест/Ext/Form/Module.bsl" : "Ext/ExternalConnectionModule.bsl";
    var uri = Path.of("src/test/resources/metadata/commonModuleCalls").resolve(relative).toUri();
    var document = TestUtils.getDocumentContext(uri, source, context);
    assertThat(document.getModuleType()).isEqualTo(moduleType);
    return document;
  }
}
