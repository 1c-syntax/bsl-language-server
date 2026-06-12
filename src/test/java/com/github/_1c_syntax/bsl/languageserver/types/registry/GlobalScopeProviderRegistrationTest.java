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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Тесты register* / unregister* API {@link GlobalScopeProvider}, работающие
 * без bsl-context (provider пустой) — для покрытия write-путей.
 */
@ExtendWith(MockitoExtension.class)
class GlobalScopeProviderRegistrationTest {

  @Mock
  private BslContextHolder bslContextHolder;
  @Mock
  private ContextProvider contextProvider;

  private GlobalScopeProvider scope;

  @BeforeEach
  void setUp() {
    // Provider пустой — GlobalScopeProvider загружается на JSON-fallback,
    // но это нам не мешает: всё, что нам нужно — функционирующий
    // GlobalSymbolScope для register*-API.
    when(bslContextHolder.get()).thenReturn(Optional.empty());
    scope = new GlobalScopeProvider(bslContextHolder, new GlobalSymbolScope());
  }

  @Test
  void registerGlobalPropertyRegistersAllAliases() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "СправочникиМенеджер");

    // when
    scope.registerGlobalProperty(ref, List.of("Справочники", "Catalogs"));

    // then — registerGlobalProperty публикует в GlobalSymbolScope как
    // SyntheticSymbol с типом-значением ref.
    assertThat(scope.findGlobal("Справочники"))
      .containsInstanceOf(SyntheticSymbol.class);
    assertThat(scope.findGlobal("catalogs")).isPresent();  // case-insensitive
    var sym = scope.findGlobal("Справочники").orElseThrow();
    assertThat(((SyntheticSymbol) sym).getValueType().qualifiedName())
      .isEqualTo("СправочникиМенеджер");
  }

  @Test
  void registerGlobalPropertyIgnoresBlankNamesAndDeclinesNullRef() {
    // when
    scope.registerGlobalProperty(null, List.of("X"));
    scope.registerGlobalProperty(new TypeRef(TypeKind.PLATFORM, "T"), List.of());
    scope.registerGlobalProperty(new TypeRef(TypeKind.PLATFORM, "T"),
      java.util.Arrays.asList("", "  ", null, "Real"));

    // then — null ref и пустой names → no-op; blank/null имена пропускаются
    assertThat(scope.findGlobal("Real")).isPresent();
    assertThat(scope.findGlobal("X")).isEmpty();
  }

  @Test
  void registerGlobalPropertyMergesScopes() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "X");

    // when — два разных скоупа на одно имя → BOTH
    scope.registerGlobalProperty(ref, List.of("X"), LanguageScope.BSL);
    scope.registerGlobalProperty(ref, List.of("X"), LanguageScope.OS);

    // then
    assertThat(scope.findGlobal("X", FileType.BSL)).isPresent();
    assertThat(scope.findGlobal("X", FileType.OS)).isPresent();
  }

  @Test
  void registerGlobalPropertyHonoursSyntheticKind() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "КодировкаТекста");

    // when
    scope.registerGlobalProperty(ref, List.of("КодировкаТекста"),
      LanguageScope.BOTH, "desc", SyntheticKind.PLATFORM_GLOBAL_ENUM);

    // then — символ виден, kind — enum
    var sym = scope.findGlobal("КодировкаТекста").orElseThrow();
    assertThat(sym).isInstanceOf(SyntheticSymbol.class);
    assertThat(((SyntheticSymbol) sym).getSyntheticKind())
      .isEqualTo(SyntheticKind.PLATFORM_GLOBAL_ENUM);
  }

  @Test
  void registerPlatformClassExposesAsClass() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "Структура");

    // when
    scope.registerPlatformClass(ref, List.of("Структура", "Structure"), LanguageScope.BOTH, "");

    // then — присутствует в classes, кодируется как TYPE_NAME
    assertThat(scope.getClasses()).contains("Структура", "Structure");
    var entry = scope.findGlobalEntry("Структура", FileType.BSL);
    assertThat(entry).map(GlobalSymbolScope.Entry::role)
      .contains(GlobalSymbolScope.Role.TYPE_NAME);
  }

  @Test
  void registerLibraryModuleAndUnregister() {
    // given
    var ref = new TypeRef(TypeKind.USER, "ФС");

    // when
    scope.registerLibraryModule("ФС", ref);

    // then
    assertThat(scope.findGlobal("ФС")).isPresent();
    assertThat(scope.findGlobal("фс")).isPresent();  // case-insensitive

    // when — снятие
    scope.unregisterLibraryModule("ФС");

    // then
    assertThat(scope.findGlobal("ФС")).isEmpty();
  }

  @Test
  void registerLibraryModuleIgnoresBlankInput() {
    // given
    var ref = new TypeRef(TypeKind.USER, "T");

    // when
    scope.registerLibraryModule("", ref);
    scope.registerLibraryModule(null, ref);
    scope.registerLibraryModule("X", null);

    // then
    assertThat(scope.findGlobal("X")).isEmpty();
  }

  @Test
  void registerLibraryClassAndUnregister() {
    // given
    var ref = new TypeRef(TypeKind.USER, "МойКласс");

    // when
    scope.registerLibraryClass("МойКласс", ref);

    // then
    var entry = scope.findGlobalEntry("МойКласс", FileType.OS);
    assertThat(entry).map(GlobalSymbolScope.Entry::role)
      .contains(GlobalSymbolScope.Role.TYPE_NAME);

    // when
    scope.unregisterLibraryClass("МойКласс");

    // then
    assertThat(scope.findGlobal("МойКласс")).isEmpty();
  }

  @Test
  void unregisterIgnoresBlankInput() {
    // when / then — не падает
    scope.unregisterLibraryModule("");
    scope.unregisterLibraryClass(null);
  }

  @Test
  void getGlobalContextsReturnsRegisteredValueSymbolsOnly() {
    // given — name символа = canonical qualifiedName ref'а; aliases — это
    // отдельный регистр в GlobalSymbolScope.
    var propRef = new TypeRef(TypeKind.PLATFORM, "ОчередьСообщений");
    var classRef = new TypeRef(TypeKind.PLATFORM, "ОчередьСтек");

    // when
    scope.registerGlobalProperty(propRef, List.of("МояОчередь"));
    scope.registerPlatformClass(classRef, List.of("ОчередьСтек"), LanguageScope.BOTH, "");

    // then — Synthetic.getName() возвращает canonical типа.
    var contexts = scope.getGlobalContexts();
    assertThat(contexts).extracting(SyntheticSymbol::getName).contains("ОчередьСообщений");
    assertThat(contexts).extracting(SyntheticSymbol::getName)
      .as("имена классов (TYPE_NAME) не попадают в global contexts")
      .doesNotContain("ОчередьСтек");
  }

  @Test
  void getGlobalContextNamesProjectsContextSymbolsToNames() {
    // given
    scope.registerGlobalProperty(
      new TypeRef(TypeKind.PLATFORM, "МойУникальныйТипПровайдер"), List.of("МойГлобал"));

    // when
    var names = scope.getGlobalContextNames();

    // then
    assertThat(names).contains("МойУникальныйТипПровайдер");
  }

  @Test
  void getGlobalContextsFilteredByFileTypeRespectsScope() {
    // given — два символа: один BSL-only, второй OS-only.
    var bslRef = new TypeRef(TypeKind.PLATFORM, "СОПТ_BslOnly");
    var osRef = new TypeRef(TypeKind.PLATFORM, "СОПТ_OsOnly");
    scope.registerGlobalProperty(bslRef, List.of("СОПТ_BslOnly"), LanguageScope.BSL);
    scope.registerGlobalProperty(osRef, List.of("СОПТ_OsOnly"), LanguageScope.OS);

    // when
    var bslNames = scope.getGlobalContexts(FileType.BSL).stream()
      .map(SyntheticSymbol::getName).toList();
    var osNames = scope.getGlobalContexts(FileType.OS).stream()
      .map(SyntheticSymbol::getName).toList();

    // then — оба списка непустые, в каждом виден только «свой» символ.
    assertThat(bslNames).contains("СОПТ_BslOnly").doesNotContain("СОПТ_OsOnly");
    assertThat(osNames).contains("СОПТ_OsOnly").doesNotContain("СОПТ_BslOnly");
  }

  @Test
  void findGlobalContextResolvesToValueType() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "СправочникиМенеджер");
    scope.registerGlobalProperty(ref, List.of("Справочники"));

    // when
    var resolved = scope.findGlobalContext("Справочники", FileType.BSL);

    // then
    assertThat(resolved).contains(ref);
  }

  @Test
  void findGlobalContextEmptyWhenScopeMismatch() {
    // given — символ зарегистрирован только в OS.
    var ref = new TypeRef(TypeKind.PLATFORM, "T");
    scope.registerGlobalProperty(ref, List.of("OS_Only"), LanguageScope.OS);

    // when / then
    assertThat(scope.findGlobalContext("OS_Only", FileType.BSL)).isEmpty();
    assertThat(scope.findGlobalContext("OS_Only", FileType.OS)).contains(ref);
  }

  @Test
  void findKeywordSnippetReturnsEmptyForBlankName() {
    // when / then
    assertThat(scope.findKeywordSnippet(null)).isEmpty();
    assertThat(scope.findKeywordSnippet("")).isEmpty();
    assertThat(scope.findKeywordSnippet("   ")).isEmpty();
  }

  @Test
  void findKeywordDescriptionReturnsEmptyForBlankName() {
    // when / then
    assertThat(scope.findKeywordDescription(null)).isEmpty();
    assertThat(scope.findKeywordDescription("")).isEmpty();
  }

  @Test
  void findKeywordSnippetEmptyForUnknownKeyword() {
    // given — builtin-keywords.json несёт сниппеты только для конкретных
    // конструкций; для произвольного имени — пусто.

    // when / then
    assertThat(scope.findKeywordSnippet("НеизвестноеКлючевоеСлово")).isEmpty();
    assertThat(scope.findKeywordSnippet("")).isEmpty();
  }

  @Test
  void findKeywordSnippetFromJsonFallback() {
    // given — bsl-context недоступен в этом тесте, источник — builtin-keywords.json.

    // when / then — сниппеты доступны по обоим написаниям (ru + en).
    assertThat(scope.findKeywordSnippet("Если")).isPresent();
    assertThat(scope.findKeywordSnippet("If")).isPresent();
  }

  @Test
  void findKeywordDescriptionFromJsonFallbackByLocale() {
    // given — bsl-context недоступен, источник — builtin-keywords.json
    // (HBK-дамп с двуязычными описаниями).

    // when / then — описание доступно по ru- и en-имени, в обеих локалях.
    assertThat(scope.findKeywordDescription("Истина",
      com.github._1c_syntax.bsl.languageserver.configuration.Language.RU))
      .isPresent();
    assertThat(scope.findKeywordDescription("True",
      com.github._1c_syntax.bsl.languageserver.configuration.Language.EN))
      .isPresent();
  }

  @Test
  void findKeywordDescriptionForPragmaCategoryIndexedButNotInCompletion() {
    // given — PRAGMA/ANNOTATION не попадают в плоский список keywords
    // (KEYWORD_CATEGORIES — только LITERAL/STATEMENT/OPERATOR/DECLARATION),
    // но описания/сниппеты для них индексируются и видны hover'у.

    // when / then
    assertThat(scope.findKeywordDescription("НаКлиенте")).isPresent();
    assertThat(scope.findKeywordDescription("AtServer")).isPresent();
  }

  @Test
  void findKeywordDescriptionContextAware() {
    // given — descriptionByParent у Возврат/Знач/Async/etc. отдаёт описание,
    // привязанное к Процедура vs Функция (СП имеет разные тексты).
    var ru = com.github._1c_syntax.bsl.languageserver.configuration.Language.RU;

    // when
    var inProcedure = scope.findKeywordDescription("Возврат", ru, "Процедура");
    var inFunction = scope.findKeywordDescription("Возврат", ru, "Функция");

    // then — оба контекста дают непустые описания, причём разные.
    assertThat(inProcedure).isPresent();
    assertThat(inFunction).isPresent();
    assertThat(inProcedure).isNotEqualTo(inFunction);
  }

  @Test
  void findGlobalCaseInsensitiveOnRegisteredSymbol() {
    // given
    scope.registerGlobalProperty(new TypeRef(TypeKind.PLATFORM, "ТипC"),
      List.of("МойГлобалC"));

    // when / then — поиск независим от регистра.
    assertThat(scope.findGlobal("мойглобалc")).isPresent();
    assertThat(scope.findGlobal("МОЙГЛОБАЛC")).isPresent();
  }

  @Test
  void findGlobalEntryReturnsEntryWithRole() {
    // given
    scope.registerGlobalProperty(new TypeRef(TypeKind.PLATFORM, "ТипR"),
      List.of("МойГлобалR"));

    // when
    var entry = scope.findGlobalEntry("МойГлобалR", FileType.BSL);

    // then
    assertThat(entry).isPresent();
    assertThat(entry.get().role()).isEqualTo(GlobalSymbolScope.Role.VALUE);
  }

  @Test
  void findGlobalPropertyBlankNameReturnsEmpty() {
    // when / then — findInList с blank name возвращает empty.
    assertThat(scope.findGlobalProperty(null)).isEmpty();
    assertThat(scope.findGlobalProperty("")).isEmpty();
    assertThat(scope.findGlobalProperty("   ")).isEmpty();
  }

  @Test
  void findGlobalEnumBlankNameReturnsEmpty() {
    // when / then
    assertThat(scope.findGlobalEnum(null)).isEmpty();
    assertThat(scope.findGlobalEnum("")).isEmpty();
  }

  @Test
  void findGlobalWithScopeMismatchReturnsEmpty() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "ТипSP");
    scope.registerGlobalProperty(ref, List.of("СП_Имя"), LanguageScope.BSL);

    // when — поиск через findGlobal с OS-fileType отсекает BSL-scope.
    var bslFound = scope.findGlobal("СП_Имя", FileType.BSL);
    var osFound = scope.findGlobal("СП_Имя", FileType.OS);

    // then
    assertThat(bslFound).isPresent();
    assertThat(osFound).isEmpty();
  }

  @Test
  void getGlobalPropertyAndEnumNamesByFileTypeWork() {
    // when / then — overload-ы не падают и возвращают список.
    assertThat(scope.getGlobalPropertyNames(FileType.BSL)).isNotNull();
    assertThat(scope.getGlobalPropertyNames(FileType.OS)).isNotNull();
    assertThat(scope.getGlobalEnumNames(FileType.BSL)).isNotNull();
    assertThat(scope.getGlobalEnumNames(FileType.OS)).isNotNull();
  }

  @Test
  void findGlobalEnumWithoutFileTypeOverload() {
    // when / then — no-arg overload.
    assertThat(scope.findGlobalEnum("НетТакогоПеречисления")).isEmpty();
  }

  @Test
  void findGlobalPropertyWithoutFileTypeOverload() {
    // when / then — no-arg overload.
    assertThat(scope.findGlobalProperty("НетТакогоСвойства")).isEmpty();
  }

  @Test
  void findFunctionByFileTypeBlankName() {
    // when / then
    assertThat(scope.findFunction(null, FileType.BSL)).isEmpty();
    assertThat(scope.findFunction("", FileType.BSL)).isEmpty();
    assertThat(scope.findFunction("   ", FileType.BSL)).isEmpty();
  }

  @Test
  void findFunctionReturnsOsDescriptorForOsFile() {
    // Функция "Сообщить" существует и в BSL (builtin-globals.json), и в OS
    // (builtin-oscript-globals.json) с разными описаниями.
    // В OS-файле должен возвращаться OS-дескриптор, а не BSL.
    var bslFn = scope.findFunction("Сообщить", FileType.BSL);
    var osFn = scope.findFunction("Сообщить", FileType.OS);

    assertThat(bslFn).isPresent();
    assertThat(osFn).isPresent();
    // Описания для BSL и OS должны отличаться (не быть одинаковыми BSL-описаниями).
    assertThat(osFn.get().description()).isNotEqualTo(bslFn.get().description());
  }

  @Test
  void findFunctionWithoutFileTypeOverloadAndBlankName() {
    // when / then — no-arg overload (без fileType).
    assertThat(scope.findFunction(null)).isEmpty();
    assertThat(scope.findFunction("")).isEmpty();
  }

  @Test
  void findKeywordDescriptionBlankOrNullName() {
    // when / then
    assertThat(scope.findKeywordDescription(null)).isEmpty();
    assertThat(scope.findKeywordDescription("")).isEmpty();
    assertThat(scope.findKeywordDescription("   ")).isEmpty();
  }

  @Test
  void findKeywordDescriptionNoArgOverload() {
    // when / then — no-arg overload использует DEFAULT_LANGUAGE.
    assertThat(scope.findKeywordDescription("НетТакогоКлючевогоСлова")).isEmpty();
  }

  @Test
  void findGlobalPropertyAndEnumAreSeparateNamespaces() {
    // given
    var propRef = new TypeRef(TypeKind.PLATFORM, "ТипPP");
    var enumRef = new TypeRef(TypeKind.PLATFORM, "ТипEE");
    scope.registerGlobalProperty(propRef, List.of("ПрG"));
    // Регистрация enum через kind в registerGlobalProperty с явным SyntheticKind.PLATFORM_GLOBAL_ENUM
    scope.registerGlobalProperty(enumRef, List.of("ПрE"),
      LanguageScope.BOTH, "", SyntheticKind.PLATFORM_GLOBAL_ENUM);

    // when / then — оба видны через findGlobal.
    assertThat(scope.findGlobal("ПрG")).isPresent();
    assertThat(scope.findGlobal("ПрE")).isPresent();
  }

  @Test
  void registerConfigurationQualifiedNameStoresAndExposes() {
    // when
    scope.registerConfigurationQualifiedName("Документы.Заказ");
    scope.registerConfigurationQualifiedName("");  // ignored
    scope.registerConfigurationQualifiedName(null);  // ignored
    scope.registerConfigurationQualifiedName("Справочники.Контрагенты");

    // then
    var names = scope.getConfigurationQualifiedNames();
    assertThat(names).contains("Документы.Заказ", "Справочники.Контрагенты");
  }
}
