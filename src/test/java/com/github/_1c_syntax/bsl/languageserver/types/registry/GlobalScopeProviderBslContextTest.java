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
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordCategory;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformLanguageKeyword;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
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

  // Реестр нужен GlobalScopeProvider лишь при резолве globalMember, а этим
  // тестам он не требуется (проверяют getClasses и getKeywords из bsl-context),
  // поэтому передаём заглушку.
  private static GlobalScopeProvider scope(ContextProvider provider) {
    return new GlobalScopeProvider(holderOf(provider),
      new TypeRegistry(List.of(), org.mockito.Mockito.mock(MemberMetadataIndex.class)));
  }

  @Test
  void classesForNewLoadedFromPlatformTypes() {
    var array = type("Массив", "Array");
    var table = type("ТаблицаЗначений", "ValueTable");

    var scope = scope(providerOf(array, table));

    assertThat(scope.getClasses(FileType.BSL)).contains("Массив", "Array", "ТаблицаЗначений", "ValueTable");
  }

  @Test
  void genericTypesNotPublishedAsClasses() {
    // Тип с угловыми скобками в имени — это шаблон, не реальный класс для Новый.
    var generic = type("СправочникСсылка.<Имя справочника>",
      "CatalogRef.<Catalog name>");
    var plain = type("Массив", "Array");

    var scope = scope(providerOf(generic, plain));

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

    var scope = scope(providerOf(
      ifKw, trueKw, procKw, pragma, annotation, pre));

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

    var scope = scope(providerOf(ifKw));

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
    var scope = scope(providerOf(ifKw));

    var byRu = scope.findKeywordSnippet("Если", FileType.BSL).orElseThrow();
    assertThat(byRu.ru()).isEqualTo("Если <?> Тогда\nКонецЕсли;");
    assertThat(byRu.en()).isEqualTo("If <?> Then\nEndIf;");

    // Тот же сниппет доступен по en-алиасу.
    var byEn = scope.findKeywordSnippet("If", FileType.BSL).orElseThrow();
    assertThat(byEn).isEqualTo(byRu);
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
