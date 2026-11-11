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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class CompletionContextRankingTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");

  @Autowired
  private CompletionProvider provider;

  @Autowired
  private TypeRegistry registry;

  @BeforeEach
  void prepareWorkspace() {
    initServerContext(ROOT);
    context.getLanguageServerConfiguration().getCompletionOptions().setHideUnavailableByContext(false);
  }

  @Test
  void unavailableLocalMethodIsRetainedBelowAnAvailableMethod() {
    var items = form("""
      &НаКлиенте
      Процедура АКлиентская()
      КонецПроцедуры
      &НаСервереБезКонтекста
      Процедура ЯСерверная()
      КонецПроцедуры
      &НаСервереБезКонтекста
      Процедура Вызов()
        |
      КонецПроцедуры
      """);

    assertBefore(items, "ЯСерверная", "АКлиентская");
    assertThat(priority(item(items, "АКлиентская"))).isEqualTo(2);
  }

  @Test
  void clientToServerTransitionKeepsTheServerMethodAvailable() {
    var items = form("""
      &НаСервере
      Процедура АКонтекстная()
      КонецПроцедуры
      &НаСервереБезКонтекста
      Процедура ЯБезКонтекста()
      КонецПроцедуры
      &НаКлиенте
      Процедура Вызов()
        |
      КонецПроцедуры
      """);

    assertThat(priority(item(items, "АКонтекстная"))).isZero();
    assertThat(priority(item(items, "ЯБезКонтекста"))).isZero();
  }

  @Test
  void aCallerWithoutFormContextDemotesTheContextualServerMethod() {
    var items = form("""
      &НаСервере
      Процедура АКонтекстная()
      КонецПроцедуры
      &НаСервереБезКонтекста
      Процедура ЯБезКонтекста()
      КонецПроцедуры
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов()
        |
      КонецПроцедуры
      """);

    assertBefore(items, "ЯБезКонтекста", "АКонтекстная");
    assertThat(priority(item(items, "АКонтекстная"))).isEqualTo(2);
  }

  @Test
  void nestedPreprocessorBranchesDefineTheCurrentContext() {
    var items = form("""
      #Если ТонкийКлиент Тогда
      &НаКлиенте
      Процедура АТонкий()
      КонецПроцедуры
      #ИначеЕсли ВебКлиент Тогда
      &НаКлиенте
      Процедура ЯВеб()
      КонецПроцедуры
      #КонецЕсли
      &НаКлиенте
      Процедура Вызов()
      #Если Клиент Тогда
      #Если ВебКлиент Тогда
        |
      #КонецЕсли
      #КонецЕсли
      КонецПроцедуры
      """);

    assertBefore(items, "ЯВеб", "АТонкий");
    assertThat(priority(item(items, "ЯВеб"))).isZero();
    assertThat(priority(item(items, "АТонкий"))).isEqualTo(2);
  }

  @Test
  void currentElseExcludesEarlierBranches() {
    var items = form("""
      #Если ТонкийКлиент Тогда
      &НаКлиенте
      Процедура АТонкий()
      КонецПроцедуры
      #КонецЕсли
      &НаКлиенте
      Процедура ЯВсегда()
      КонецПроцедуры
      &НаКлиенте
      Процедура Вызов()
      #Если ТонкийКлиент Тогда
      #Иначе
        |
      #КонецЕсли
      КонецПроцедуры
      """);

    assertBefore(items, "ЯВсегда", "АТонкий");
    assertThat(priority(item(items, "АТонкий"))).isEqualTo(2);
  }

  @Test
  void dotCompletionUsesTheServerSideOfARemoteCall() {
    module("СерверныйМодульВызовСервера", """
      #Если Сервер Тогда
      Процедура ЯДоступная() Экспорт
      КонецПроцедуры
      #Иначе
      Процедура АНедоступная() Экспорт
      КонецПроцедуры
      #КонецЕсли
      """);
    var items = form("""
      &НаКлиенте
      Процедура Вызов()
        СерверныйМодульВызовСервера.|
      КонецПроцедуры
      """);

    assertBefore(items, "ЯДоступная", "АНедоступная");
    assertThat(priority(item(items, "ЯДоступная"))).isZero();
  }

  @Test
  void commonModuleIdentifiersRespectServerCallVisibility() {
    var items = form("""
      &НаКлиенте
      Процедура Вызов()
        СерверныйМодуль|
      КонецПроцедуры
      """);

    assertBefore(items, "СерверныйМодульВызовСервера", "СерверныйМодуль");
  }

  @Test
  void clientCommonModuleRanksBelowServerModuleBeforeItsFileIsParsed() {
    initServerContext(ROOT, false);
    context.getLanguageServerConfiguration().getCompletionOptions().setHideUnavailableByContext(false);
    var items = form("""
      &AtServerNoContext
      Procedure Caller()
        |
      EndProcedure
      """);

    assertBefore(items, "СерверныйМодуль", "КлиентскийМодуль");
  }

  @Test
  void fieldsFromAnExcludedCompilationBranchAreNotOffered() {
    var items = form("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов()
      #Если Сервер Тогда
        Значение = Новый Структура("АПервое", 1);
      #Иначе
        Значение = Новый Структура("ЯВторое", 1);
      #КонецЕсли
      #Если ВебКлиент Тогда
        Значение.|
      #КонецЕсли
      КонецПроцедуры
      """);

    assertThat(priority(item(items, "ЯВторое"))).isZero();
    assertThat(items).extracting(CompletionItem::getLabel).doesNotContain("АПервое");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void commonModuleRankingUsesTheProjectFileModePolicy(boolean fileMode) {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(fileMode);
    var items = form("""
      &AtClient
      Procedure Caller()
        |
      EndProcedure
      """);

    assertThat(priority(item(items, "КлиентскийМодуль"))).isZero();
    assertThat(priority(item(items, "СерверныйМодуль"))).isEqualTo(fileMode ? 1 : 2);
    assertBefore(items, "КлиентскийМодуль", "СерверныйМодуль");
  }

  @Test
  void receiverReturnedByAFunctionIsInferredInTheCurrentContext() {
    var items = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Коллекция()
      #Если Сервер Тогда
        Возврат Новый Соответствие;
      #Иначе
        Возврат Новый Массив;
      #КонецЕсли
      КонецФункции
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов()
      #Если Сервер Тогда
        Коллекция().|
      #КонецЕсли
      КонецПроцедуры
      """);

    assertThat(priority(item(items, "Вставить"))).isZero();
    assertThat(items).extracting(CompletionItem::getLabel).doesNotContain("Добавить");
  }

  @Test
  void availabilityUsesTheInsertedAliasAcrossDifferentReceiverTypes() {
    module("КлиентскийМодуль", "Процедура Add(Значение) Экспорт\nКонецПроцедуры\n");
    var document = spy(TestUtils.getDocumentContext(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), """
      &НаКлиенте
      Процедура Вызов(Условие)
        Если Условие Тогда
          Получатель = КлиентскийМодуль;
        Иначе
          Получатель = Новый Массив;
        КонецЕсли;
        Получатель.
      КонецПроцедуры
      """, context));
    doReturn(Language.EN).when(document).getScriptVariantLanguage();
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(document.getUri().toString()));
    params.setPosition(new Position(7, "  Получатель.".length()));

    var items = provider.getCompletion(document, params).getItems();

    assertThat(items).filteredOn(candidate -> candidate.getLabel().equals("Add"))
      .isNotEmpty().allSatisfy(candidate -> assertThat(priority(candidate)).isZero());
  }

  @Test
  void partialAvailabilityHasItsOwnGroup() {
    var items = complete(ROOT.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), """
      #Если Клиент Тогда
      Процедура БЧастично()
      КонецПроцедуры
      #КонецЕсли
      #Если Сервер И НЕ Сервер Тогда
      Процедура АНедоступная()
      КонецПроцедуры
      #КонецЕсли
      Процедура ЯВсегда()
      КонецПроцедуры
      Процедура Вызов()
        |
      КонецПроцедуры
      """);

    assertThat(priority(item(items, "ЯВсегда"))).isZero();
    assertThat(priority(item(items, "БЧастично"))).isEqualTo(1);
    assertThat(priority(item(items, "АНедоступная"))).isEqualTo(2);
  }

  @Test
  void anAvailableGlobalRanksAboveItsExcludedLocalNamesake() {
    var items = form("""
      #Если ТонкийКлиент Тогда
      &НаКлиенте
      Процедура Сообщить()
      КонецПроцедуры
      #КонецЕсли
      &НаКлиенте
      Процедура Вызов()
      #Если ВебКлиент Тогда
        Сообщ|
      #КонецЕсли
      КонецПроцедуры
      """);

    assertThat(items.stream().filter(item -> item.getLabel().equals("Сообщить"))
      .mapToInt(CompletionContextRankingTest::priority).toArray()).containsExactlyInAnyOrder(0, 2);
  }

  @Test
  void qualifiedSelfMemberDoesNotBorrowAvailabilityFromAPlatformGlobal() {
    var items = form("""
      #Если ТонкийКлиент Тогда
      &НаКлиенте
      Процедура Сообщить() Экспорт
      КонецПроцедуры
      #КонецЕсли
      &НаКлиенте
      Процедура ЯВсегда() Экспорт
      КонецПроцедуры
      &НаКлиенте
      Процедура Вызов()
      #Если ВебКлиент Тогда
        ЭтотОбъект.|
      #КонецЕсли
      КонецПроцедуры
      """);

    assertBefore(items, "ЯВсегда", "Сообщить");
    assertThat(priority(item(items, "Сообщить"))).isEqualTo(2);
  }

  @Test
  void platformFunctionsUseMetadataAndUnknownAvailabilityIsNotPenalized() {
    registry.registerMemberOverride(registry.intern(TypeKind.PLATFORM, "ГлобальныйКонтекст"), () -> List.of(
      MemberDescriptor.method("КонтекстАСервер", "", List.of()).withMetadata(metadata(Set.of(Availability.SERVER))),
      MemberDescriptor.method("КонтекстБНеизвестно", "", List.of()),
      MemberDescriptor.method("КонтекстЯКлиент", "", List.of()).withMetadata(metadata(Set.of(
        Availability.THIN_CLIENT, Availability.WEB_CLIENT, Availability.MOBILE_CLIENT, Availability.THICK_CLIENT)))
    ), FileType.BSL);
    var items = form("""
      &НаКлиенте
      Процедура Вызов()
        Контекст|
      КонецПроцедуры
      """);

    assertBefore(items, "КонтекстЯКлиент", "КонтекстАСервер");
    assertThat(priority(item(items, "КонтекстБНеизвестно"))).isZero();
  }

  @Test
  void constructorTypesUsePlatformAvailability() {
    registry.registerTypeMetadata(registry.resolve("Массив").orElseThrow(),
      metadata(Set.of(Availability.SERVER)), FileType.BSL);
    registry.registerTypeMetadata(registry.resolve("Структура").orElseThrow(),
      metadata(Set.of(Availability.THIN_CLIENT, Availability.WEB_CLIENT,
        Availability.MOBILE_CLIENT, Availability.THICK_CLIENT)), FileType.BSL);
    var items = form("""
      &НаКлиенте
      Процедура Вызов()
        Значение = Новый |
      КонецПроцедуры
      """);

    assertBefore(items, "Структура", "Массив");
  }

  @Test
  void deprecationOrderIsPreservedInsideTheAvailableGroup() {
    var items = form("""
      &НаСервере
      Процедура АНедоступная()
      КонецПроцедуры
      // Устарела. Используйте ЯНовая.
      &НаСервереБезКонтекста
      Процедура БСтарая()
      КонецПроцедуры
      &НаСервереБезКонтекста
      Процедура ЯНовая()
      КонецПроцедуры
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов()
        |
      КонецПроцедуры
      """);

    assertBefore(items, "ЯНовая", "БСтарая");
    assertBefore(items, "БСтарая", "АНедоступная");
  }

  @ParameterizedTest
  @ValueSource(strings = {"bsl", "os"})
  void missingMetadataAndOneScriptKeepTheOriginalSortKeys(String extension) {
    initServerContext();
    var items = complete(URI.create("file:///completion." + extension), """
      Процедура АПервая()
      КонецПроцедуры
      Процедура ЯВторая()
      КонецПроцедуры
      Процедура Вызов()
        |
      КонецПроцедуры
      """);

    assertThat(item(items, "АПервая").getSortText()).isEqualTo("10_АПервая");
    assertThat(item(items, "ЯВторая").getSortText()).isEqualTo("10_ЯВторая");
  }

  @Test
  void unknownPreprocessorConditionKeepsTheOriginalSortKeys() {
    var items = form("""
      &НаКлиенте
      Процедура АКлиентская()
      КонецПроцедуры
      &НаСервере
      Процедура ЯСерверная()
      КонецПроцедуры
      &НаСервере
      Процедура Вызов()
      #Если Windows Тогда
        |
      #КонецЕсли
      КонецПроцедуры
      """);

    assertThat(item(items, "АКлиентская").getSortText()).isEqualTo("10_АКлиентская");
  }

  private List<CompletionItem> form(String source) {
    return complete(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source);
  }

  private void module(String name, String source) {
    TestUtils.getDocumentContext(ROOT.resolve("CommonModules/" + name + "/Ext/Module.bsl").toUri(), source, context);
  }

  private List<CompletionItem> complete(URI uri, String markedSource) {
    var marker = markedSource.indexOf('|');
    var before = markedSource.substring(0, marker);
    var line = (int) before.chars().filter(value -> value == '\n').count();
    var column = before.length() - before.lastIndexOf('\n') - 1;
    var document = TestUtils.getDocumentContext(uri, markedSource.replace("|", ""), context);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
    params.setPosition(new Position(line, column));
    return provider.getCompletion(document, params).getItems();
  }

  private static PlatformMetadata metadata(Set<Availability> availability) {
    return new PlatformMetadata("", "", List.of(), availability, null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());
  }

  private static CompletionItem item(List<CompletionItem> items, String label) {
    return items.stream().filter(item -> item.getLabel().equals(label)).findFirst().orElseThrow();
  }

  private static int priority(CompletionItem item) {
    var sort = item.getSortText();
    return sort.length() > 1 && sort.charAt(1) == '_' ? Character.digit(sort.charAt(0), 10) : 0;
  }

  private static void assertBefore(List<CompletionItem> items, String first, String second) {
    assertThat(item(items, first).getSortText()).isLessThan(item(items, second).getSortText());
  }
}
