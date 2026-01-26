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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class BslDocSemanticTokensSupplierTest {

  @Autowired
  private BslDocSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @BeforeEach
  void init() {
    supplier.setMultilineTokenSupport(false);
  }

  @Test
  void testMethodDescription() {
    // given
    String bsl = """
      // Описание метода
      Процедура Тест()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - Description should be Comment with Documentation modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(0, 0, 18, SemanticTokenTypes.Comment,
        Set.of(SemanticTokenModifiers.Documentation), "// Описание метода")
    ));
  }

  @Test
  void testParametersKeyword() {
    // given
    String bsl = """
      // Описание метода
      // Параметры:
      //  Параметр1 - Строка - описание параметра
      Процедура Тест(Параметр1)
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - "Параметры:" should be Macro with Documentation modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 3, 10, SemanticTokenTypes.Macro,
        Set.of(SemanticTokenModifiers.Documentation), "Параметры:")
    ));
  }

  @Test
  void testParameterNames() {
    // given
    String bsl = """
      // Описание метода
      // Параметры:
      //  Параметр1 - Строка - описание
      Процедура Тест(Параметр1)
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - Parameter name in description should be Parameter with Documentation modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 4, 9, SemanticTokenTypes.Parameter,
        Set.of(SemanticTokenModifiers.Documentation), "Параметр1")
    ));
  }

  @Test
  void testReturnsKeyword() {
    // given
    String bsl = """
      // Описание функции
      // Возвращаемое значение:
      //  Строка - результат
      Функция Тест()
        Возврат "";
      КонецФункции
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - "Возвращаемое значение:" should be Macro with Documentation modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 3, 22, SemanticTokenTypes.Macro,
        Set.of(SemanticTokenModifiers.Documentation), "Возвращаемое значение:")
    ));
  }

  @Test
  void testTypeNames() {
    // given
    String bsl = """
      // Описание метода
      // Параметры:
      //  Параметр1 - Строка - описание
      Процедура Тест(Параметр1)
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - Type "Строка" should be Type with Documentation modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 16, 6, SemanticTokenTypes.Type,
        Set.of(SemanticTokenModifiers.Documentation), "Строка")
    ));
  }

  @Test
  void testDeprecatedKeyword() {
    // given
    String bsl = """
      // Устарела. Использовать НовыйМетод
      Процедура СтарыйМетод()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - "Устарела." should be Macro with Documentation modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(0, 3, 9, SemanticTokenTypes.Macro,
        Set.of(SemanticTokenModifiers.Documentation), "Устарела.")
    ));
  }

  @Test
  void testMultipleTypesOnSeparateLines() {
    // given - parameter with multiple types on separate lines
    String bsl = """
      // Описание метода
      // Параметры:
      //  Параметр - СправочникСсылка
      //                  - ДокументСсылка
      //                  - ПеречислениеСсылка - описание параметра
      Процедура Тест(Параметр)
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - All three types should be Type with Documentation modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 15, 16, SemanticTokenTypes.Type,
        Set.of(SemanticTokenModifiers.Documentation), "СправочникСсылка"),
      new ExpectedToken(3, 22, 14, SemanticTokenTypes.Type,
        Set.of(SemanticTokenModifiers.Documentation), "ДокументСсылка"),
      new ExpectedToken(4, 22, 18, SemanticTokenTypes.Type,
        Set.of(SemanticTokenModifiers.Documentation), "ПеречислениеСсылка")
    ));
  }

  @Test
  void testMultipleReturnTypesOnSeparateLines() {
    // given - return value with multiple types on separate lines
    String bsl = """
      // Описание функции
      // Возвращаемое значение:
      //  СправочникСсылка
      //  - ДокументСсылка - результат
      Функция Тест()
        Возврат Неопределено;
      КонецФункции
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - Both types should be Type with Documentation modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 4, 16, SemanticTokenTypes.Type,
        Set.of(SemanticTokenModifiers.Documentation), "СправочникСсылка"),
      new ExpectedToken(3, 6, 14, SemanticTokenTypes.Type,
        Set.of(SemanticTokenModifiers.Documentation), "ДокументСсылка")
    ));
  }

  @Test
  void testMultilineSupport() {
    // given
    String bsl = """
      // Первая строка описания
      // Вторая строка описания
      // Третья строка описания
      Процедура Тест()
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // Test without multiline support - should have 3 separate comment tokens (one per line)
    supplier.setMultilineTokenSupport(false);
    var tokensWithoutMultiline = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    helper.assertContainsTokens(tokensWithoutMultiline, List.of(
      new ExpectedToken(0, 0, 25, SemanticTokenTypes.Comment,
        Set.of(SemanticTokenModifiers.Documentation), "// Первая строка описания"),
      new ExpectedToken(1, 0, 25, SemanticTokenTypes.Comment,
        Set.of(SemanticTokenModifiers.Documentation), "// Вторая строка описания"),
      new ExpectedToken(2, 0, 25, SemanticTokenTypes.Comment,
        Set.of(SemanticTokenModifiers.Documentation), "// Третья строка описания")
    ));

    // Test with multiline support - should merge consecutive lines into one token
    supplier.setMultilineTokenSupport(true);
    var tokensWithMultiline = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // With multiline support, all 3 lines are merged into single token starting at line 0
    // Length is 77 (25 + 1 + 25 + 1 + 25 = 77 including newlines)
    helper.assertContainsTokens(tokensWithMultiline, List.of(
      new ExpectedToken(0, 0, 77, SemanticTokenTypes.Comment,
        Set.of(SemanticTokenModifiers.Documentation), "// Первая строка описания...")
    ));
  }
}

