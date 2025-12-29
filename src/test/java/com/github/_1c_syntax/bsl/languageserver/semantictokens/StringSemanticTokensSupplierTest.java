/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class StringSemanticTokensSupplierTest {

  @Autowired
  private StringSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  private List<SemanticTokenEntry> tokens(String bsl) {
    var documentContext = TestUtils.getDocumentContext(bsl);
    return supplier.getSemanticTokens(documentContext);
  }

  private int typeIndex(String semanticTokenType) {
    return legend.getTokenTypes().indexOf(semanticTokenType);
  }

  private List<SemanticTokenEntry> tokensOfType(List<SemanticTokenEntry> tokens, String semanticTokenType) {
    int typeIdx = typeIndex(semanticTokenType);
    return tokens.stream()
      .filter(t -> t.type() == typeIdx)
      .toList();
  }

  // ==================== Regular String Tests ====================

  @Test
  void testSimpleString() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = "Привет мир";
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);
    assertThat(stringTokens).hasSize(1);
  }

  @Test
  void testMultilineString() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = "Первая строка
        |Вторая строка
        |Третья строка";
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);
    // STRINGSTART, 2x STRINGPART, or STRINGTAIL
    assertThat(stringTokens).hasSizeGreaterThanOrEqualTo(3);
  }

  // ==================== NStr Tests ====================

  @Test
  void testNStrLanguageKeys() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = НСтр("ru='Привет'; en='Hello'");
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    // ru, en
    assertThat(propertyTokens).hasSize(2);

    // Check that string parts are also present
    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);
    assertThat(stringTokens).isNotEmpty();
  }

  @Test
  void testNStrEnglishName() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = NStr("ru='Привет'; en='Hello'");
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    // ru, en
    assertThat(propertyTokens).hasSize(2);
  }

  // ==================== StrTemplate Tests ====================

  @Test
  void testStrTemplatePlaceholders() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = СтрШаблон("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1, %2
    assertThat(parameterTokens).hasSize(2);

    // Check that string parts are also present
    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);
    assertThat(stringTokens).isNotEmpty();
  }

  @Test
  void testStrTemplateEnglishName() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = StrTemplate("Name: %1, version: %2", Name, Version);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1, %2
    assertThat(parameterTokens).hasSize(2);
  }

  @Test
  void testStrTemplatePlaceholdersWithParentheses() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = СтрШаблон("%(1)%(2)", "Первая", "Вторая");
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %(1), %(2)
    assertThat(parameterTokens).hasSize(2);
  }

  @Test
  void testStrTemplateWithVariable() {
    // given - шаблон задан в переменной, затем используется в СтрШаблон
    String bsl = """
      Процедура Тест()
        НовыйШаблон = "%1 %2";
        Результат = СтрШаблон(НовыйШаблон, А, Б);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then - плейсхолдеры в строке-присвоении должны подсвечиваться
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1, %2 из строки НовыйШаблон = "%1 %2"
    assertThat(parameterTokens).hasSize(2);
  }

  @Test
  void testStrTemplateWithVariableAndParentheses() {
    // given - шаблон с %(1) синтаксисом в переменной
    String bsl = """
      Процедура Тест()
        Шаблон = "%(1)%(2)";
        Текст = СтрШаблон(Шаблон, "Первая", "Вторая");
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %(1), %(2)
    assertThat(parameterTokens).hasSize(2);
  }

  // ==================== Combined NStr + StrTemplate Tests ====================

  @Test
  void testNStrInsideStrTemplate() {
    // given - НСтр внутри СтрШаблон: СтрШаблон(НСтр("ru = 'Текст %1'"), Параметр)
    String bsl = """
      Процедура Тест()
        Сообщить(СтрШаблон(НСтр("ru = 'Сценарий %1'"), Параметр));
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then - должны быть и языковые ключи (ru), и плейсхолдеры (%1)
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    // ru
    assertThat(propertyTokens).hasSize(1);

    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1
    assertThat(parameterTokens).hasSize(1);
  }

  @Test
  void testNStrInsideStrTemplateMultiple() {
    // given - НСтр с несколькими языками и несколькими плейсхолдерами
    String bsl = """
      Процедура Тест()
        Результат = СтрШаблон(НСтр("ru = 'Привет %1'; en = 'Hello %2'"), Имя, Name);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    // ru, en
    assertThat(propertyTokens).hasSize(2);

    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1, %2
    assertThat(parameterTokens).hasSize(2);
  }

  @Test
  void testNStrVariableInStrTemplate() {
    // given - НСтр присвоен переменной, которая затем используется в СтрШаблон
    String bsl = """
      Процедура Тест()
        Шаблон = НСтр("ru = 'Сценарий %1'");
        ТекстПредупреждения = СтрШаблон(Шаблон, Параметр);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then - должны быть и языковые ключи (ru), и плейсхолдеры (%1)
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    // ru
    assertThat(propertyTokens).hasSize(1);

    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1
    assertThat(parameterTokens).hasSize(1);
  }

  @Test
  void testNStrVariableInStrTemplateMultiple() {
    // given - НСтр с несколькими языками и плейсхолдерами в переменной
    String bsl = """
      Процедура Тест()
        Шаблон = НСтр("ru = 'Привет %1 и %2'; en = 'Hello %1 and %2'");
        Результат = СтрШаблон(Шаблон, Имя1, Имя2);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    // ru, en
    assertThat(propertyTokens).hasSize(2);

    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1, %2 (по одному разу в каждой подстроке, но токен один - значит 4 плейсхолдера)
    // Нет, здесь один строковый токен, внутри которого 4 вхождения %N
    assertThat(parameterTokens).hasSize(4);
  }

  // ==================== Query String Tests ====================

  @Test
  void testQueryStringSplit() {
    // given
    String bsl = """
      Процедура Тест()
        Запрос.Текст = "ВЫБРАТЬ Ссылка ИЗ Справочник.Номенклатура";
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    // String parts should be split around query tokens
    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);

    // Should have multiple string parts (quotes and spaces around keywords)
    assertThat(stringTokens).hasSizeGreaterThan(1);
  }

  @Test
  void testMultilineQueryString() {
    // given
    String bsl = """
      Процедура Тест()
        Запрос.Текст = "ВЫБРАТЬ
        |  Ссылка
        |ИЗ
        |  Справочник.Номенклатура";
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);

    // Should have string parts on each line
    assertThat(stringTokens).isNotEmpty();
  }

  // ==================== Mixed Context Tests ====================

  @Test
  void testNStrAndQueryInSameMethod() {
    // given
    String bsl = """
      Процедура Тест()
        Сообщение = НСтр("ru='Выполняется запрос'");
        Запрос.Текст = "ВЫБРАТЬ Ссылка ИЗ Справочник.Номенклатура";
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    // ru from NStr
    assertThat(propertyTokens).hasSize(1);

    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);
    // Should have string parts from both NStr and query
    assertThat(stringTokens).hasSizeGreaterThan(2);
  }

  @Test
  void testRegularStringNotAffectedByOtherContexts() {
    // given
    String bsl = """
      Процедура Тест()
        ОбычнаяСтрока = "Это просто строка без НСтр и запроса";
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);
    // Single string token for the whole string
    assertThat(stringTokens).hasSize(1);

    // No Property or Parameter tokens
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    assertThat(propertyTokens).isEmpty();

    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    assertThat(parameterTokens).isEmpty();
  }

  // ==================== SDBL Query Token Tests ====================

  @Test
  void testSimpleSelect() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * из Справочник.Контрагенты";
      КонецФункции
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var keywordTokens = tokensOfType(tokens, SemanticTokenTypes.Keyword);
    var namespaceTokens = tokensOfType(tokens, SemanticTokenTypes.Namespace);
    var classTokens = tokensOfType(tokens, SemanticTokenTypes.Class);

    // Выбрать, из
    assertThat(keywordTokens).hasSizeGreaterThanOrEqualTo(2);
    // Справочник
    assertThat(namespaceTokens).hasSize(1);
    // Контрагенты
    assertThat(classTokens).hasSize(1);
  }

  @Test
  void testQueryWithParameter() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * где Код = &Параметр";
      КонецФункции
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);

    // &Параметр - один объединённый токен
    assertThat(parameterTokens).hasSize(1);
    assertThat(parameterTokens.get(0).line()).isEqualTo(1);
  }

  @Test
  void testQueryWithVirtualTable() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * из РегистрСведений.КурсыВалют.СрезПоследних()";
      КонецФункции
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var methodTokens = tokensOfType(tokens, SemanticTokenTypes.Method);

    // СрезПоследних
    assertThat(methodTokens).hasSize(1);
  }

  @Test
  void testQueryWithValueFunction() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * где Валюта = Значение(Справочник.Валюты.Рубль)";
      КонецФункции
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var namespaceTokens = tokensOfType(tokens, SemanticTokenTypes.Namespace);
    var classTokens = tokensOfType(tokens, SemanticTokenTypes.Class);
    var enumMemberTokens = tokensOfType(tokens, SemanticTokenTypes.EnumMember);

    // Справочник
    assertThat(namespaceTokens).hasSize(1);
    // Валюты
    assertThat(classTokens).hasSize(1);
    // Рубль
    assertThat(enumMemberTokens).hasSize(1);
  }

  @Test
  void testQueryWithEnumValue() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * где Пол = Значение(Перечисление.Пол.Мужской)";
      КонецФункции
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var enumTokens = tokensOfType(tokens, SemanticTokenTypes.Enum);
    var enumMemberTokens = tokensOfType(tokens, SemanticTokenTypes.EnumMember);

    // Пол (enum)
    assertThat(enumTokens).hasSize(1);
    // Мужской (enum member)
    assertThat(enumMemberTokens).hasSize(1);
  }

  @Test
  void testQueryWithAggregateFunction() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать Количество(*) из Справочник.Товары";
      КонецФункции
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var functionTokens = tokensOfType(tokens, SemanticTokenTypes.Function);

    // Количество
    assertThat(functionTokens).hasSize(1);
  }

  // ==================== Configurable Template Function Tests ====================

  @Test
  void testSubstituteParametersToStringPlaceholders() {
    // given - СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку like СтрШаблон
    String bsl = """
      Процедура Тест()
        Текст = СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1, %2
    assertThat(parameterTokens).hasSize(2);

    // Check that string parts are also present
    var stringTokens = tokensOfType(tokens, SemanticTokenTypes.String);
    assertThat(stringTokens).isNotEmpty();
  }

  @Test
  void testSubstituteParametersToStringEnglish() {
    // given - English variant of the function
    String bsl = """
      Процедура Тест()
        Text = StringFunctionsClientServer.SubstituteParametersToString("Name: %1, version: %2", Name, Version);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1, %2
    assertThat(parameterTokens).hasSize(2);
  }

  @Test
  void testSubstituteParametersToStringWithVariable() {
    // given - template stored in variable, then used in module function call
    String bsl = """
      Процедура Тест()
        Шаблон = "%1 + %2 = %3";
        Текст = СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(Шаблон, А, Б, В);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then - placeholders in the assigned string should be highlighted
    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1, %2, %3
    assertThat(parameterTokens).hasSize(3);
  }

  @Test
  void testNStrWithSubstituteParametersToString() {
    // given - НСтр combined with ПодставитьПараметрыВСтроку
    String bsl = """
      Процедура Тест()
        Шаблон = НСтр("ru = 'Привет %1'");
        Текст = СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(Шаблон, Имя);
      КонецПроцедуры
      """;

    // when
    var tokens = tokens(bsl);

    // then - should have both language keys (ru) and placeholders (%1)
    var propertyTokens = tokensOfType(tokens, SemanticTokenTypes.Property);
    // ru
    assertThat(propertyTokens).hasSize(1);

    var parameterTokens = tokensOfType(tokens, SemanticTokenTypes.Parameter);
    // %1
    assertThat(parameterTokens).hasSize(1);
  }
}

