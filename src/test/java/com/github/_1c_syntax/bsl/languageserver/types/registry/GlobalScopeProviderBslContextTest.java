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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordCategory;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnum;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnumValue;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethod;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethodSignature;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProperty;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextSignatureParameter;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import com.github._1c_syntax.bsl.context.platform.PlatformLanguageKeyword;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты на загрузку BSL-части {@link GlobalScopeProvider} из bsl-context:
 * глобальные функции, классы для {@code Новый}, ключевые слова, платформенные
 * переменные (системные перечисления + свойства глобального контекста),
 * сниппеты автодополнения. Источник — синтетический {@link PlatformContextProvider}
 * без чтения реального HBK.
 */
class GlobalScopeProviderBslContextTest {

  private static BslContextHolder holderOf(ContextProvider provider) {
    return new BslContextHolder(null) {
      @Override
      public Optional<ContextProvider> get() {
        return Optional.ofNullable(provider);
      }
    };
  }

  @Test
  void globalFunctionsLoadedFromContext() {
    var globalContext = PlatformGlobalContext.builder()
      .methods(new ArrayList<>(List.of(
        method("Сообщить", "Message", "Выводит сообщение."),
        method("СтрДлина", "StrLen", "Длина строки.")
      )))
      .properties(Collections.emptyList())
      .applicationEvents(Collections.emptyList())
      .ordinaryApplicationEvents(Collections.emptyList())
      .sessionModuleEvents(Collections.emptyList())
      .externalConnectionModuleEvents(Collections.emptyList())
      .build();

    var scope = new GlobalScopeProvider(holderOf(providerOf(globalContext)), new GlobalSymbolScope());

    assertThat(scope.findFunction("Сообщить", FileType.BSL)).isPresent();
    assertThat(scope.findFunction("Message", FileType.BSL)).isPresent();
    assertThat(scope.findFunction("СтрДлина", FileType.BSL).orElseThrow().description())
      .isEqualTo("Длина строки.");
  }

  @Test
  void classesForNewLoadedFromPlatformTypes() {
    var array = type("Массив", "Array");
    var table = type("ТаблицаЗначений", "ValueTable");

    var scope = new GlobalScopeProvider(holderOf(providerOf(array, table)), new GlobalSymbolScope());

    assertThat(scope.getClasses(FileType.BSL)).contains("Массив", "Array", "ТаблицаЗначений", "ValueTable");
  }

  @Test
  void genericTypesNotPublishedAsClasses() {
    // Тип с угловыми скобками в имени — это шаблон, не реальный класс для Новый.
    var generic = type("СправочникСсылка.<Имя справочника>",
      "CatalogRef.<Catalog name>");
    var plain = type("Массив", "Array");

    var scope = new GlobalScopeProvider(holderOf(providerOf(generic, plain)), new GlobalSymbolScope());

    assertThat(scope.getClasses(FileType.BSL)).contains("Массив", "Array");
    assertThat(scope.getClasses(FileType.BSL)).doesNotContain("СправочникСсылка.<Имя справочника>");
  }

  @Test
  void languageKeywordsByCategory() {
    var ifKw = keyword("Если", "If", LanguageKeywordCategory.STATEMENT,
      "Если <?> Тогда\nКонецЕсли;", "If <?> Then\nEndIf;");
    var trueKw = keyword("Истина", "True", LanguageKeywordCategory.LITERAL,
      "Истина", "True");
    var procKw = keyword("Процедура", "Procedure", LanguageKeywordCategory.DECLARATION,
      "Процедура <?>()\nКонецПроцедуры", "Procedure <?>()\nEndProcedure");
    // PRAGMA / ANNOTATION / PREPROCESSOR_INSTRUCTION в keywords не идут.
    var pragma = keyword("НаКлиенте", "AtClient", LanguageKeywordCategory.PRAGMA, "", "");
    var annotation = keyword("Перед", "Before", LanguageKeywordCategory.ANNOTATION, "", "");
    var pre = keyword("Область", "Region", LanguageKeywordCategory.PREPROCESSOR_INSTRUCTION, "", "");

    var scope = new GlobalScopeProvider(holderOf(providerOf(
      ifKw, trueKw, procKw, pragma, annotation, pre)), new GlobalSymbolScope());

    assertThat(scope.getKeywords(FileType.BSL)).contains("Если", "If", "Истина", "True", "Процедура", "Procedure");
    assertThat(scope.getKeywords(FileType.BSL)).doesNotContain("НаКлиенте", "Перед", "Область");
  }

  @Test
  void keywordDescriptionPropagatesToFindKeywordDescription() {
    // Регрессия: hover на keyword'е должен показывать description из синтакс-помощника.
    // Описание берётся из bsl-context через GlobalScopeProvider.findKeywordDescription.
    var ifKw = PlatformLanguageKeyword.builder()
      .name(new ContextName("Если", "If"))
      .category(LanguageKeywordCategory.STATEMENT)
      .description("Используется для разветвления алгоритма.")
      .snippet(LanguageKeywordSnippet.EMPTY)
      .build();

    var scope = new GlobalScopeProvider(holderOf(providerOf(ifKw)), new GlobalSymbolScope());

    assertThat(scope.findKeywordDescription("Если", Language.DEFAULT_LANGUAGE, null, FileType.BSL))
      .as("description у keyword'а должно проброситься из bsl-context'а")
      .contains("Используется для разветвления алгоритма.");
    // По en-алиасу — то же описание.
    assertThat(scope.findKeywordDescription("If", Language.DEFAULT_LANGUAGE, null, FileType.BSL))
      .contains("Используется для разветвления алгоритма.");
  }

  @Test
  void keywordSnippetIsBilingualAndAccessibleByEitherName() {
    var ifKw = keyword("Если", "If", LanguageKeywordCategory.STATEMENT,
      "Если <?> Тогда\nКонецЕсли;", "If <?> Then\nEndIf;");
    var scope = new GlobalScopeProvider(holderOf(providerOf(ifKw)), new GlobalSymbolScope());

    var byRu = scope.findKeywordSnippet("Если", FileType.BSL).orElseThrow();
    assertThat(byRu.ru()).isEqualTo("Если <?> Тогда\nКонецЕсли;");
    assertThat(byRu.en()).isEqualTo("If <?> Then\nEndIf;");

    // Тот же сниппет доступен по en-алиасу.
    var byEn = scope.findKeywordSnippet("If", FileType.BSL).orElseThrow();
    assertThat(byEn).isEqualTo(byRu);
  }

  @Test
  void contextEnumPublishedAsPlatformEnum() {
    var encoding = PlatformContextEnum.builder()
      .name(new ContextName("КодировкаТекста", "TextEncoding"))
      .values(List.of(
        new PlatformContextEnumValue(new ContextName("UTF8", "UTF8"),
          "", "", "", List.of())
      ))
      .build();

    var scope = new GlobalScopeProvider(holderOf(providerOf(encoding)), new GlobalSymbolScope());

    assertThat(scope.getGlobalEnumNames(FileType.BSL)).contains("КодировкаТекста");
    assertThat(scope.getGlobalPropertyNames(FileType.BSL)).doesNotContain("КодировкаТекста");
    var type = scope.findGlobalEnum("КодировкаТекста", FileType.BSL).orElseThrow();
    assertThat(type.qualifiedName()).isEqualTo("КодировкаТекста");
    // Поиск по en-алиасу тоже работает.
    assertThat(scope.findGlobalEnum("TextEncoding", FileType.BSL)).isPresent();
  }

  @Test
  void globalContextPropertiesPublishedAsPlatformVariables() {
    var catalogsManager = type("СправочникиМенеджер", "CatalogsManager");

    var catalogsProperty = PlatformContextProperty.builder()
      .name(new ContextName("Справочники", "Catalogs"))
      .rawTypes(List.of("СправочникиМенеджер"))
      .description("Глобальное свойство для доступа к справочникам.")
      .availabilities(List.of())
      .build();

    var globalContext = PlatformGlobalContext.builder()
      .methods(Collections.emptyList())
      .properties(new ArrayList<>(List.of(catalogsProperty)))
      .applicationEvents(Collections.emptyList())
      .ordinaryApplicationEvents(Collections.emptyList())
      .sessionModuleEvents(Collections.emptyList())
      .externalConnectionModuleEvents(Collections.emptyList())
      .build();

    var scope = new GlobalScopeProvider(holderOf(providerOf(catalogsManager, globalContext)), new GlobalSymbolScope());

    assertThat(scope.getGlobalPropertyNames(FileType.BSL)).contains("Справочники");
    // Тип «Справочники» — это СправочникиМенеджер (для dot-completion'а).
    assertThat(scope.findGlobalProperty("Справочники", FileType.BSL).orElseThrow().qualifiedName())
      .isEqualTo("СправочникиМенеджер");
    // En-алиас тоже находит.
    assertThat(scope.findGlobalProperty("Catalogs", FileType.BSL)).isPresent();
  }

  @Test
  void genericGlobalPropertyIsSkipped() {
    // У СправочникиМенеджер есть generic-property «<Имя справочника>» — это
    // плейсхолдер, а не реальное глобальное имя; не должен попадать в platformVariables.
    var genericProp = PlatformContextProperty.builder()
      .name(new ContextName("<Имя справочника>", "<Catalog name>"))
      .rawTypes(List.of())
      .description("")
      .availabilities(List.of())
      .build();

    var globalContext = PlatformGlobalContext.builder()
      .methods(Collections.emptyList())
      .properties(new ArrayList<>(List.of(genericProp)))
      .applicationEvents(Collections.emptyList())
      .ordinaryApplicationEvents(Collections.emptyList())
      .sessionModuleEvents(Collections.emptyList())
      .externalConnectionModuleEvents(Collections.emptyList())
      .build();

    var scope = new GlobalScopeProvider(holderOf(providerOf(globalContext)), new GlobalSymbolScope());
    // Generic-placeholder отфильтрован: в BSL-наборе его нет ни в каком виде.
    assertThat(scope.getGlobalPropertyNames(FileType.BSL)).doesNotContain("<Имя справочника>");
    // OS-набор из oscript-JSON не зависит от bsl-context и не пуст.
    assertThat(scope.getGlobalPropertyNames(FileType.OS)).isNotEmpty();
  }

  // --- builders ---

  private static PlatformContextType type(String ru, String en) {
    return PlatformContextType.builder()
      .name(new ContextName(ru, en))
      .methods(Collections.emptyList())
      .properties(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .build();
  }

  private static PlatformContextMethod method(String ru, String en, String description) {
    var sig = PlatformContextMethodSignature.builder()
      .name(new ContextName("", ""))
      .parameters(new ArrayList<ContextSignatureParameter>())
      .description("")
      .build();
    return PlatformContextMethod.builder()
      .name(new ContextName(ru, en))
      .description(description)
      .availabilities(List.of())
      .rawReturnValues(List.of())
      .signatures(new ArrayList<>(List.of((ContextMethodSignature) sig)))
      .build();
  }

  private static PlatformLanguageKeyword keyword(String ru, String en,
                                                 LanguageKeywordCategory category,
                                                 String snippetRu, String snippetEn) {
    return PlatformLanguageKeyword.builder()
      .name(new ContextName(ru, en))
      .category(category)
      .description("")
      .snippet(snippetRu.isEmpty() && snippetEn.isEmpty()
        ? LanguageKeywordSnippet.EMPTY
        : new LanguageKeywordSnippet(snippetRu, snippetEn))
      .build();
  }

  private static ContextProvider providerOf(Context... contexts) {
    return new PlatformContextProvider(
      new PlatformContextStorage(new ArrayList<>(List.of(contexts))));
  }
}
