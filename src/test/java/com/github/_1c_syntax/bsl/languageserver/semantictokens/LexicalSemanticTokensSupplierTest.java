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
class LexicalSemanticTokensSupplierTest {

  @Autowired
  private LexicalSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void testKeywords() {
    // given
    String bsl = """
      Процедура Тест()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(0, 0, 9, SemanticTokenTypes.Keyword, "Процедура"),
      new ExpectedToken(1, 0, 14, SemanticTokenTypes.Keyword, "КонецПроцедуры")
    ));
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

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - String tokens are handled by StringSemanticTokensSupplier
    // No String type tokens expected from LexicalSemanticTokensSupplier at position where the string is
    assertThat(decoded.stream()
      .filter(t -> t.start() == 10 && t.line() == 1)
      .toList()).isEmpty();
  }

  @Test
  void testNumbers() {
    // given
    String bsl = """
      Процедура Тест()
        Число = 123;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 10, 3, SemanticTokenTypes.Number, "123")
    ));
  }

  @Test
  void testOperators() {
    // given
    String bsl = """
      Процедура Тест()
        А = 1 + 2;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - =, + should be operators
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 4, 1, SemanticTokenTypes.Operator, "="),
      new ExpectedToken(1, 8, 1, SemanticTokenTypes.Operator, "+")
    ));
  }

  @Test
  void testDateTime() {
    // given
    String bsl = """
      Процедура Тест()
        Дата = '20231225';
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - DateTime is highlighted as String
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 9, 10, SemanticTokenTypes.String, "'20231225'")
    ));
  }

  @Test
  void testLiterals() {
    // given
    String bsl = """
      Процедура Тест()
        А = Истина;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - Истина is Keyword
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 6, 6, SemanticTokenTypes.Keyword, "Истина")
    ));
  }

  @Test
  void testQueryStringsAreSkipped() {
    // given - Query strings should be skipped (handled by QuerySemanticTokensSupplier)
    String bsl = """
      Процедура Тест()
        Запрос = "Выбрать * из Справочник.Контрагенты";
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - The query string should NOT be present as a single String token
    var stringTokensOnQueryLine = decoded.stream()
      .filter(t -> t.line() == 1 && t.start() >= 11 && t.start() < 40)
      .toList();

    // Query string is skipped - no full string token at that position
    assertThat(stringTokensOnQueryLine).isEmpty();
  }
}
