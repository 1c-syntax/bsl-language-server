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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class StringSemanticTokensSupplierTest {

  @Autowired
  private StringSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - one string token
    helper.assertTokensMatch(decoded, List.of(
      new ExpectedToken(1, 10, 12, SemanticTokenTypes.String, "\"Привет мир\"")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - multiple string parts
    helper.assertTokensMatch(decoded, List.of(
      new ExpectedToken(1, 10, 14, SemanticTokenTypes.String, "\"Первая строка"),
      new ExpectedToken(2, 2, 14, SemanticTokenTypes.String, "|Вторая строка"),
      new ExpectedToken(3, 2, 15, SemanticTokenTypes.String, "|Третья строка\"")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - language keys (ru, en) are highlighted as Property
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 16, 2, SemanticTokenTypes.Property, "ru"),
      new ExpectedToken(1, 29, 2, SemanticTokenTypes.Property, "en")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - NStr works same as НСтр
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 16, 2, SemanticTokenTypes.Property, "ru"),
      new ExpectedToken(1, 29, 2, SemanticTokenTypes.Property, "en")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - placeholders %1, %2 are highlighted as Parameter
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 35, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 47, 2, SemanticTokenTypes.Parameter, "%2")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 29, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 42, 2, SemanticTokenTypes.Parameter, "%2")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - %(1) and %(2) syntax
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 21, 4, SemanticTokenTypes.Parameter, "%(1)"),
      new ExpectedToken(1, 25, 4, SemanticTokenTypes.Parameter, "%(2)")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - плейсхолдеры в строке-присвоении должны подсвечиваться
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 17, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 20, 2, SemanticTokenTypes.Parameter, "%2")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 12, 4, SemanticTokenTypes.Parameter, "%(1)"),
      new ExpectedToken(1, 16, 4, SemanticTokenTypes.Parameter, "%(2)")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - должны быть и языковые ключи (ru), и плейсхолдеры (%1)
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 27, 2, SemanticTokenTypes.Property, "ru"),
      new ExpectedToken(1, 42, 2, SemanticTokenTypes.Parameter, "%1")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 30, 2, SemanticTokenTypes.Property, "ru"),
      new ExpectedToken(1, 43, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 48, 2, SemanticTokenTypes.Property, "en"),
      new ExpectedToken(1, 60, 2, SemanticTokenTypes.Parameter, "%2")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - должны быть и языковые ключи (ru), и плейсхолдеры (%1)
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 17, 2, SemanticTokenTypes.Property, "ru"),
      new ExpectedToken(1, 32, 2, SemanticTokenTypes.Parameter, "%1")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 17, 2, SemanticTokenTypes.Property, "ru"),
      new ExpectedToken(1, 30, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 35, 2, SemanticTokenTypes.Parameter, "%2"),
      new ExpectedToken(1, 40, 2, SemanticTokenTypes.Property, "en"),
      new ExpectedToken(1, 52, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 59, 2, SemanticTokenTypes.Parameter, "%2")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - query keywords are highlighted
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 18, 7, SemanticTokenTypes.Keyword, "ВЫБРАТЬ"),
      new ExpectedToken(1, 33, 2, SemanticTokenTypes.Keyword, "ИЗ"),
      new ExpectedToken(1, 36, 10, SemanticTokenTypes.Namespace, "Справочник"),
      new ExpectedToken(1, 47, 12, SemanticTokenTypes.Class, "Номенклатура")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 18, 7, SemanticTokenTypes.Keyword, "ВЫБРАТЬ"),
      new ExpectedToken(3, 3, 2, SemanticTokenTypes.Keyword, "ИЗ"),
      new ExpectedToken(4, 5, 10, SemanticTokenTypes.Namespace, "Справочник"),
      new ExpectedToken(4, 16, 12, SemanticTokenTypes.Class, "Номенклатура")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      // NStr tokens
      new ExpectedToken(1, 20, 2, SemanticTokenTypes.Property, "ru"),
      // Query tokens
      new ExpectedToken(2, 18, 7, SemanticTokenTypes.Keyword, "ВЫБРАТЬ"),
      new ExpectedToken(2, 36, 10, SemanticTokenTypes.Namespace, "Справочник")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - just one string token
    helper.assertTokensMatch(decoded, List.of(
      new ExpectedToken(1, 18, 38, SemanticTokenTypes.String, "\"Это просто строка без НСтр и запроса\"")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 12, 7, SemanticTokenTypes.Keyword, "Выбрать"),
      new ExpectedToken(1, 22, 2, SemanticTokenTypes.Keyword, "из"),
      new ExpectedToken(1, 25, 10, SemanticTokenTypes.Namespace, "Справочник"),
      new ExpectedToken(1, 36, 11, SemanticTokenTypes.Class, "Контрагенты")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - Query parameter has readonly modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 32, 9, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Readonly, "&Параметр")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 52, 13, SemanticTokenTypes.Method, "СрезПоследних")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 44, 10, SemanticTokenTypes.Namespace, "Справочник"),
      new ExpectedToken(1, 55, 6, SemanticTokenTypes.Class, "Валюты"),
      new ExpectedToken(1, 62, 5, SemanticTokenTypes.EnumMember, "Рубль")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 41, 12, SemanticTokenTypes.Namespace, "Перечисление"),
      new ExpectedToken(1, 54, 3, SemanticTokenTypes.Enum, "Пол"),
      new ExpectedToken(1, 58, 7, SemanticTokenTypes.EnumMember, "Мужской")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - Aggregate function has defaultLibrary modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 20, 10, SemanticTokenTypes.Function, SemanticTokenModifiers.DefaultLibrary, "Количество")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 81, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 93, 2, SemanticTokenTypes.Parameter, "%2")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 73, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 86, 2, SemanticTokenTypes.Parameter, "%2")
    ));
  }

  @Test
  void testSubstituteParametersToStringLocal() {
    // given - Local call without module prefix (configured in defaults)
    String bsl = """
      Процедура Тест()
        Текст = ПодставитьПараметрыВСтроку("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 52, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 64, 2, SemanticTokenTypes.Parameter, "%2")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - placeholders in the assigned string should be highlighted
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 12, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 17, 2, SemanticTokenTypes.Parameter, "%2"),
      new ExpectedToken(1, 22, 2, SemanticTokenTypes.Parameter, "%3")
    ));
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
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - should have both language keys (ru) and placeholders (%1)
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 17, 2, SemanticTokenTypes.Property, "ru"),
      new ExpectedToken(1, 30, 2, SemanticTokenTypes.Parameter, "%1")
    ));
  }

  // ==================== Case-Insensitive Method Name Tests ====================

  @Test
  void testStrTemplateUpperCase() {
    // given - СтрШаблон in uppercase
    String bsl = """
      Процедура Тест()
        Текст = СТРШАБЛОН("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - placeholders should still be highlighted
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 35, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 47, 2, SemanticTokenTypes.Parameter, "%2")
    ));
  }

  @Test
  void testStrTemplateMixedCase() {
    // given - СтрШаблон in mixed case
    String bsl = """
      Процедура Тест()
        Текст = стрШаблон("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 35, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 47, 2, SemanticTokenTypes.Parameter, "%2")
    ));
  }

  @Test
  void testNStrUpperCase() {
    // given - НСтр in uppercase
    String bsl = """
      Процедура Тест()
        Текст = НСТР("ru='Привет'; en='Hello'");
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - language keys should still be highlighted
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 16, 2, SemanticTokenTypes.Property, "ru"),
      new ExpectedToken(1, 29, 2, SemanticTokenTypes.Property, "en")
    ));
  }

  @Test
  void testSubstituteParametersToStringUpperCase() {
    // given - local method call in uppercase
    String bsl = """
      Процедура Тест()
        Текст = ПОДСТАВИТЬПАРАМЕТРЫВСТРОКУ("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - placeholders should be highlighted
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 52, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 64, 2, SemanticTokenTypes.Parameter, "%2")
    ));
  }

  @Test
  void testSubstituteParametersToStringMixedCase() {
    // given - local method call in mixed case
    String bsl = """
      Процедура Тест()
        Текст = подставитьПараметрыВСтроку("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 52, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 64, 2, SemanticTokenTypes.Parameter, "%2")
    ));
  }

  @Test
  void testModuleMethodCallUpperCase() {
    // given - module.method call in uppercase
    String bsl = """
      Процедура Тест()
        Текст = СТРОКОВЫЕФУНКЦИИКЛИЕНТСЕРВЕР.ПОДСТАВИТЬПАРАМЕТРЫВСТРОКУ("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 81, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 93, 2, SemanticTokenTypes.Parameter, "%2")
    ));
  }

  @Test
  void testModuleMethodCallMixedCase() {
    // given - module.method call in mixed case
    String bsl = """
      Процедура Тест()
        Текст = СтроковыеФункцииКлиентсервер.подставитьПараметрыВстроку("Наименование: %1, версия: %2", Наименование, Версия);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 81, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 93, 2, SemanticTokenTypes.Parameter, "%2")
    ));
  }

  @Test
  void testEnglishSubstituteParametersToStringUpperCase() {
    // given - English variant in uppercase
    String bsl = """
      Процедура Тест()
        Text = STRINGFUNCTIONSCLIENTSERVER.SUBSTITUTEPARAMETERSTOSTRING("Name: %1, version: %2", Name, Version);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 73, 2, SemanticTokenTypes.Parameter, "%1"),
      new ExpectedToken(1, 86, 2, SemanticTokenTypes.Parameter, "%2")
    ));
  }

  // ==================== Lambda String Tests (OS files only) ====================

  @Test
  void testBslFileReturnsNoLambdaTokens() {
    // given - BSL file should not produce lambda tokens even if string contains ->
    String bsl = """
      Процедура Тест()
        Р = "А -> Возврат А > 5";
      КонецПроцедуры
      """;

    // when - BSL context (default)
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - just a plain string token, no lambda sub-tokens
    helper.assertTokensMatch(decoded, List.of(
      new ExpectedToken(1, 6, 20, SemanticTokenTypes.String, "\"А -> Возврат А > 5\"")
    ));
  }

  @Test
  void testLambdaSimpleExpression() {
    // given - OS file with lambda in any string containing ->
    String os = """
      Процедура Тест()
        Р = "А -> Возврат А > 5";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then - param А, arrow ->, and body tokens
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 7, 1, SemanticTokenTypes.Parameter, "А"),
      new ExpectedToken(1, 9, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(1, 12, 7, SemanticTokenTypes.Keyword, "Возврат"),
      new ExpectedToken(1, 22, 1, SemanticTokenTypes.Operator, ">"),
      new ExpectedToken(1, 24, 1, SemanticTokenTypes.Number, "5")
    ));
  }

  @Test
  void testLambdaDetectedInAnyString() {
    // given - Lambda not in Лямбда.Выражение, but in arbitrary function
    String os = """
      Процедура Тест()
        Р = МояФункция("А -> Возврат А + 1");
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then - should detect lambda by -> regardless of method name
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 18, 1, SemanticTokenTypes.Parameter, "А"),
      new ExpectedToken(1, 20, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(1, 23, 7, SemanticTokenTypes.Keyword, "Возврат"),
      new ExpectedToken(1, 33, 1, SemanticTokenTypes.Operator, "+"),
      new ExpectedToken(1, 35, 1, SemanticTokenTypes.Number, "1")
    ));
  }

  @Test
  void testLambdaRegularStringWithoutArrowNotAffected() {
    // given - OS file, string without -> should be plain STRING
    String os = """
      Процедура Тест()
        Текст = "Возврат Истина";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then - plain string, no lambda processing
    helper.assertTokensMatch(decoded, List.of(
      new ExpectedToken(1, 10, 16, SemanticTokenTypes.String, "\"Возврат Истина\"")
    ));
  }

  @Test
  void testLambdaWithBooleanLiteral() {
    // given
    String os = """
      Процедура Тест()
        Р = "Х -> Истина";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 7, 1, SemanticTokenTypes.Parameter, "Х"),
      new ExpectedToken(1, 9, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(1, 12, 6, SemanticTokenTypes.Keyword, "Истина")
    ));
  }

  @Test
  void testLambdaWithIfKeyword() {
    // given
    String os = """
      Процедура Тест()
        Р = "Х -> Если Х > 0 Тогда Возврат Х КонецЕсли";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 7, 1, SemanticTokenTypes.Parameter, "Х"),
      new ExpectedToken(1, 9, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(1, 12, 4, SemanticTokenTypes.Keyword, "Если"),
      new ExpectedToken(1, 19, 1, SemanticTokenTypes.Operator, ">"),
      new ExpectedToken(1, 21, 1, SemanticTokenTypes.Number, "0"),
      new ExpectedToken(1, 23, 5, SemanticTokenTypes.Keyword, "Тогда"),
      new ExpectedToken(1, 29, 7, SemanticTokenTypes.Keyword, "Возврат"),
      new ExpectedToken(1, 39, 9, SemanticTokenTypes.Keyword, "КонецЕсли")
    ));
  }

  @Test
  void testLambdaScreenshotExample() {
    // given - realistic lambda usage
    String os = """
      Если Элемент.Фильтровать("Элемент -> Элемент.Задержка() <= 0 ").СодержитЗначение() Тогда
        Результат = Очередь.Взять();
      КонецЕсли;
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then - param, arrow, operators and number inside lambda body should be highlighted
    assertThat(decoded).isNotEmpty();
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(0, 26, 7, SemanticTokenTypes.Parameter, "Элемент"),
      new ExpectedToken(0, 34, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(0, 44, 1, SemanticTokenTypes.Operator, "."),
      new ExpectedToken(0, 53, 1, SemanticTokenTypes.Operator, "("),
      new ExpectedToken(0, 54, 1, SemanticTokenTypes.Operator, ")"),
      new ExpectedToken(0, 56, 2, SemanticTokenTypes.Operator, "<="),
      new ExpectedToken(0, 59, 1, SemanticTokenTypes.Number, "0")
    ));
  }

  @Test
  void testLambdaMultilineString() {
    // given - Multiline string literal with lambda
    String os = """
      Процедура Тест()
        Р = "Х ->
        | Возврат Х + 1";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then - param and arrow on first line, body tokens on the second line
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 7, 1, SemanticTokenTypes.Parameter, "Х"),
      new ExpectedToken(1, 9, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(2, 4, 7, SemanticTokenTypes.Keyword, "Возврат"),
      new ExpectedToken(2, 14, 1, SemanticTokenTypes.Operator, "+"),
      new ExpectedToken(2, 16, 1, SemanticTokenTypes.Number, "1")
    ));
  }

  @Test
  void testLambdaMultipleParameters() {
    // given - lambda with multiple parameters in parentheses
    String os = """
      Процедура Тест()
        Р = "(А, Б) -> А + Б";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then - parens, params, commas, arrow, and body operators
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 7, 1, SemanticTokenTypes.Operator, "("),
      new ExpectedToken(1, 8, 1, SemanticTokenTypes.Parameter, "А"),
      new ExpectedToken(1, 9, 1, SemanticTokenTypes.Operator, ","),
      new ExpectedToken(1, 11, 1, SemanticTokenTypes.Parameter, "Б"),
      new ExpectedToken(1, 12, 1, SemanticTokenTypes.Operator, ")"),
      new ExpectedToken(1, 14, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(1, 19, 1, SemanticTokenTypes.Operator, "+")
    ));
  }

  @Test
  void testLambdaParameterReferencesHighlighted() {
    // given - lambda with parameters used in body
    String os = """
      Процедура Тест()
        Р = "(Элемент) -> Элемент + 1";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then - param in header, arrow, param reference in body, operator, number
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 7, 1, SemanticTokenTypes.Operator, "("),
      new ExpectedToken(1, 8, 7, SemanticTokenTypes.Parameter, "Элемент"),
      new ExpectedToken(1, 15, 1, SemanticTokenTypes.Operator, ")"),
      new ExpectedToken(1, 17, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(1, 20, 7, SemanticTokenTypes.Parameter, "Элемент (ref)"),
      new ExpectedToken(1, 28, 1, SemanticTokenTypes.Operator, "+"),
      new ExpectedToken(1, 30, 1, SemanticTokenTypes.Number, "1")
    ));
  }

  @Test
  void testLambdaWithAnnotations() {
    // given - lambda with annotations before parameters
    String os = """
      Процедура Тест()
        Р = "&Набор &Завязь(""Имя"", Тип = ""Значение"") () -> 1";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then - annotations, annotation params, lambda parens, arrow, body
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 7, 1, SemanticTokenTypes.Decorator, "&"),
      new ExpectedToken(1, 8, 5, SemanticTokenTypes.Decorator, "Набор"),
      new ExpectedToken(1, 14, 1, SemanticTokenTypes.Decorator, "&"),
      new ExpectedToken(1, 15, 6, SemanticTokenTypes.Decorator, "Завязь"),
      new ExpectedToken(1, 21, 1, SemanticTokenTypes.Operator, "("),
      new ExpectedToken(1, 22, 5, SemanticTokenTypes.String, "\"Имя\" (mapped from cleaned)"),
      new ExpectedToken(1, 29, 1, SemanticTokenTypes.Operator, ","),
      new ExpectedToken(1, 31, 3, SemanticTokenTypes.Parameter, "Тип"),
      new ExpectedToken(1, 35, 1, SemanticTokenTypes.Operator, "="),
      new ExpectedToken(1, 37, 10, SemanticTokenTypes.String, "\"Значение\" (mapped from cleaned)"),
      new ExpectedToken(1, 49, 1, SemanticTokenTypes.Operator, ")"),
      new ExpectedToken(1, 51, 1, SemanticTokenTypes.Operator, "("),
      new ExpectedToken(1, 52, 1, SemanticTokenTypes.Operator, ")"),
      new ExpectedToken(1, 54, 2, SemanticTokenTypes.Operator, "->"),
      new ExpectedToken(1, 57, 1, SemanticTokenTypes.Number, "1")
    ));
  }

  @Test
  void testNestedLambdaHighlighting() {
    // given — multiline outer lambda with inline inner lambda ("" Параметр -> ..."")
    // and multiline inner lambda (""\n|...\n|"")
    String os = "Процедура Тест()\n"
      + "  Р = \"Результат, Настройка ->\n"
      + "  |\n"
      + "  |  Результат.Слить(\n"
      + "  |    Настройка.Ключ,\n"
      + "  |    Настройка.Значение,\n"
      + "  |    \"\" Параметр -> Параметр + 1\"\",\n"
      + "  |    \"\"\n"
      + "  |    |Старое, Новое ->\n"
      + "  |    |  Если ТипЗнч(Старое) = Тип(\"\"\"\"МножествоСоответствие\"\"\"\") Тогда\n"
      + "  |    |    Старое.ДобавитьВсе(Новое);\n"
      + "  |    |  КонецЕсли;\n"
      + "  |    |\n"
      + "  |    |  Возврат Старое;\n"
      + "  |    |\"\"\n"
      + "  |  );\n"
      + "  |\n"
      + "  |  Возврат Результат;\n"
      + "  |\";\n"
      + "КонецПроцедуры\n";

    // when
    var decoded = helper.getDecodedTokensForOs(os, supplier);

    // then — outer lambda params
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 7, 9, SemanticTokenTypes.Parameter, "Результат"),
      new ExpectedToken(1, 18, 9, SemanticTokenTypes.Parameter, "Настройка"),
      new ExpectedToken(1, 28, 2, SemanticTokenTypes.Operator, "outer ->")
    ));

    // inline inner lambda on line 6: "" Параметр -> Параметр + 1""
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(6, 10, 8, SemanticTokenTypes.Parameter, "Параметр (inline inner param)"),
      new ExpectedToken(6, 19, 2, SemanticTokenTypes.Operator, "-> (inline inner arrow)"),
      new ExpectedToken(6, 33, 1, SemanticTokenTypes.Number, "1 (inline inner body)")
    ));

    // multiline inner lambda on lines 8+: Старое, Новое ->
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(8, 8, 6, SemanticTokenTypes.Parameter, "Старое (nested param)"),
      new ExpectedToken(8, 16, 5, SemanticTokenTypes.Parameter, "Новое (nested param)"),
      new ExpectedToken(8, 22, 2, SemanticTokenTypes.Operator, "-> (nested arrow)"),
      new ExpectedToken(9, 10, 4, SemanticTokenTypes.Keyword, "Если (nested body keyword)")
    ));
  }
}