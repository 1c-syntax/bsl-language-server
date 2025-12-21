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
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BslDocSemanticTokensSupplierTest {

  @Autowired
  private BslDocSemanticTokensSupplier supplier;

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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - Description should be Comment with Documentation modifier
    int commentTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Comment);
    int docModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Documentation);

    var docCommentTokens = tokens.stream()
      .filter(t -> t.type() == commentTypeIdx && (t.modifiers() & docModifierMask) != 0)
      .toList();

    assertThat(docCommentTokens).isNotEmpty();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - "Параметры:" should be Macro with Documentation modifier
    int macroTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Macro);
    int docModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Documentation);

    var macroTokens = tokens.stream()
      .filter(t -> t.type() == macroTypeIdx && (t.modifiers() & docModifierMask) != 0)
      .toList();

    assertThat(macroTokens).isNotEmpty();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - Parameter name in description should be Parameter with Documentation modifier
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    int docModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Documentation);

    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx && (t.modifiers() & docModifierMask) != 0)
      .toList();

    assertThat(parameterTokens).isNotEmpty();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - "Возвращаемое значение:" should be Macro with Documentation modifier
    int macroTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Macro);
    int docModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Documentation);

    var macroTokens = tokens.stream()
      .filter(t -> t.type() == macroTypeIdx && (t.modifiers() & docModifierMask) != 0)
      .toList();

    assertThat(macroTokens).isNotEmpty();
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - Type "Строка" should be Type with Documentation modifier
    int typeTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    int docModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Documentation);

    var typeTokens = tokens.stream()
      .filter(t -> t.type() == typeTypeIdx && (t.modifiers() & docModifierMask) != 0)
      .toList();

    assertThat(typeTokens).isNotEmpty();
  }

  @Test
  void testDeprecatedKeyword() {
    // given
    String bsl = """
      // Устарела. Использовать НовыйМетод
      Процедура СтарыйМетод()
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - "Устарела." should be Macro with Documentation modifier
    int macroTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Macro);
    int docModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Documentation);

    var macroTokens = tokens.stream()
      .filter(t -> t.type() == macroTypeIdx && (t.modifiers() & docModifierMask) != 0)
      .toList();

    assertThat(macroTokens).isNotEmpty();
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
    var tokensWithoutMultiline = supplier.getSemanticTokens(documentContext);

    // Test with multiline support
    supplier.setMultilineTokenSupport(true);
    var tokensWithMultiline = supplier.getSemanticTokens(documentContext);

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

