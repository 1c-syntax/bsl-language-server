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
class LexicalSemanticTokensSupplierTest {

  @Autowired
  private LexicalSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  @Test
  void testKeywords() {
    // given
    String bsl = """
      Процедура Тест()
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int keywordTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    var keywordTokens = tokens.stream()
      .filter(t -> t.type() == keywordTypeIdx)
      .toList();
    // Процедура, КонецПроцедуры
    assertThat(keywordTokens).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void testStrings() {
    // Note: STRING tokens are now handled by StringSemanticTokensSupplier
    // This test verifies that LexicalSemanticTokensSupplier does NOT process regular strings
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
    // String tokens are handled by StringSemanticTokensSupplier, so should be 0
    assertThat(stringTokens).isEmpty();
  }

  @Test
  void testNumbers() {
    // given
    String bsl = """
      Процедура Тест()
        Число = 123;
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int numberTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Number);
    var numberTokens = tokens.stream()
      .filter(t -> t.type() == numberTypeIdx)
      .toList();
    assertThat(numberTokens).hasSize(1);
  }

  @Test
  void testOperators() {
    // given
    String bsl = """
      Процедура Тест()
        А = 1 + 2;
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int operatorTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    var operatorTokens = tokens.stream()
      .filter(t -> t.type() == operatorTypeIdx)
      .toList();
    // =, +, ;
    assertThat(operatorTokens).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void testDateTime() {
    // given
    String bsl = """
      Процедура Тест()
        Дата = '20231225';
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
    // DateTime is highlighted as String
    assertThat(stringTokens).hasSize(1);
  }

  @Test
  void testLiterals() {
    // given
    String bsl = """
      Процедура Тест()
        А = Истина;
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int keywordTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    var keywordTokens = tokens.stream()
      .filter(t -> t.type() == keywordTypeIdx && t.line() == 1)
      .toList();
    // Истина is Keyword
    assertThat(keywordTokens).hasSize(1);
  }

  @Test
  void testQueryStringsAreSkipped() {
    // given - Query strings should be skipped (handled by QuerySemanticTokensSupplier)
    String bsl = """
      Процедура Тест()
        Запрос = "Выбрать * из Справочник.Контрагенты";
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - The query string should NOT be present as a single String token
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokensOnQueryLine = tokens.stream()
      .filter(t -> t.type() == stringTypeIdx && t.line() == 1)
      .toList();

    // Query string is skipped - no full string token at that position
    assertThat(stringTokensOnQueryLine).isEmpty();
  }
}

