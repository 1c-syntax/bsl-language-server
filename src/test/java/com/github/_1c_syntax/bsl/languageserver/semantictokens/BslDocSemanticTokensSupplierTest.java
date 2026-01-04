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
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class BslDocSemanticTokensSupplierTest {

  @Autowired
  private BslDocSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Autowired
  private SemanticTokensLegend legend;

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
    int typeTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    int docModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Documentation);

    var typeTokens = decoded.stream()
      .filter(t -> t.type() == typeTypeIdx && (t.modifiers() & docModifierMask) != 0)
      .toList();

    // Should have 3 type tokens: СправочникСсылка, ДокументСсылка, ПеречислениеСсылка
    assertThat(typeTokens).hasSize(3);
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
    int typeTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    int docModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Documentation);

    var typeTokens = decoded.stream()
      .filter(t -> t.type() == typeTypeIdx && (t.modifiers() & docModifierMask) != 0)
      .toList();

    // Should have 2 type tokens: СправочникСсылка, ДокументСсылка
    assertThat(typeTokens).hasSize(2);
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

    // Test without multiline support
    supplier.setMultilineTokenSupport(false);
    var tokensWithoutMultiline = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // Test with multiline support
    supplier.setMultilineTokenSupport(true);
    var tokensWithMultiline = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // Without multiline: should have 3 separate comment tokens (one per line)
    // With multiline: may merge consecutive lines into fewer tokens
    int commentTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Comment);

    var commentTokensWithout = tokensWithoutMultiline.stream()
      .filter(t -> t.type() == commentTypeIdx)
      .toList();
    var commentTokensWith = tokensWithMultiline.stream()
      .filter(t -> t.type() == commentTypeIdx)
      .toList();

    // With multiline support, we expect fewer or equal number of tokens
    assertThat(commentTokensWith.size()).isLessThanOrEqualTo(commentTokensWithout.size());
  }
}

