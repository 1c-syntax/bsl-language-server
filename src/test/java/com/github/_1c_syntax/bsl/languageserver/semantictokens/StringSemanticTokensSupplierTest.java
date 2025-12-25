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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class StringSemanticTokensSupplierTest {

  @Autowired
  private StringSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  // ==================== Regular String Tests ====================

  @Test
  void testSimpleString() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = "Привет мир";
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int propertyTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Property);
    var propertyTokens = tokens.stream()
      .filter(t -> t.type() == propertyTypeIdx)
      .toList();
    // ru, en
    assertThat(propertyTokens).hasSize(2);

    // Check that string parts are also present
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int propertyTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Property);
    var propertyTokens = tokens.stream()
      .filter(t -> t.type() == propertyTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
    // %1, %2
    assertThat(parameterTokens).hasSize(2);

    // Check that string parts are also present
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - плейсхолдеры в строке-присвоении должны подсвечиваться
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
    // %(1), %(2)
    assertThat(parameterTokens).hasSize(2);
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    // String parts should be split around query tokens
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx)
      .toList();

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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx)
      .toList();

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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int propertyTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Property);
    var propertyTokens = tokens.stream()
      .filter(t -> t.type() == propertyTypeIdx)
      .toList();
    // ru from NStr
    assertThat(propertyTokens).hasSize(1);

    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx)
      .toList();
    // Single string token for the whole string
    assertThat(stringTokens).hasSize(1);

    // No Property or Parameter tokens
    int propertyTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Property);
    var propertyTokens = tokens.stream()
      .filter(t -> t.type() == propertyTypeIdx)
      .toList();
    assertThat(propertyTokens).isEmpty();

    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int keywordTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    int namespaceTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    int classTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Class);

    var keywordTokens = tokens.stream().filter(t -> t.type() == keywordTypeIdx).toList();
    var namespaceTokens = tokens.stream().filter(t -> t.type() == namespaceTypeIdx).toList();
    var classTokens = tokens.stream().filter(t -> t.type() == classTypeIdx).toList();

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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    var parameterTokens = tokens.stream().filter(t -> t.type() == parameterTypeIdx).toList();

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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int methodTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
    var methodTokens = tokens.stream().filter(t -> t.type() == methodTypeIdx).toList();

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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int namespaceTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    int classTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Class);
    int enumMemberTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.EnumMember);

    var namespaceTokens = tokens.stream().filter(t -> t.type() == namespaceTypeIdx).toList();
    var classTokens = tokens.stream().filter(t -> t.type() == classTypeIdx).toList();
    var enumMemberTokens = tokens.stream().filter(t -> t.type() == enumMemberTypeIdx).toList();

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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int enumTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Enum);
    int enumMemberTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.EnumMember);

    var enumTokens = tokens.stream().filter(t -> t.type() == enumTypeIdx).toList();
    var enumMemberTokens = tokens.stream().filter(t -> t.type() == enumMemberTypeIdx).toList();

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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int functionTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Function);
    var functionTokens = tokens.stream().filter(t -> t.type() == functionTypeIdx).toList();

    // Количество
    assertThat(functionTokens).hasSize(1);
  }
}

