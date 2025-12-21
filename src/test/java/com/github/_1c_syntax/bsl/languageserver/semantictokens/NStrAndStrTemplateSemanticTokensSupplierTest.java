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
class NStrAndStrTemplateSemanticTokensSupplierTest {

  @Autowired
  private NStrAndStrTemplateSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

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
  void testStrTemplateWithPlaceholder10() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = СтрШаблон("%1 %2 %3 %4 %5 %6 %7 %8 %9 %10", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
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
    // %1 through %10
    assertThat(parameterTokens).hasSize(10);
  }

  @Test
  void testNStrWithNestedStrTemplate() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = СтрШаблон(НСтр("ru='Наименование: %1'"), Наименование);
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int propertyTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Property);
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);

    var propertyTokens = tokens.stream()
      .filter(t -> t.type() == propertyTypeIdx)
      .toList();
    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();

    // ru
    assertThat(propertyTokens).hasSize(1);
    // %1
    assertThat(parameterTokens).hasSize(1);
  }

  @Test
  void testMultilineNStr() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = НСтр("ru='Первая строка
          |Вторая строка'; en='First line
          |Second line'");
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
    // ru, en - language keys are in the first string token
    assertThat(propertyTokens).hasSizeGreaterThanOrEqualTo(1);
  }

  @Test
  void testEmptyParams() {
    // given - no crash when there are no parameters
    String bsl = """
      Процедура Тест()
        Текст = НСтр();
        Текст2 = СтрШаблон();
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isEmpty();
  }

  @Test
  void testNoHighlightForOtherMethods() {
    // given
    String bsl = """
      Процедура Тест()
        Текст = Сообщить("ru='Привет' %1");
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isEmpty();
  }
}
