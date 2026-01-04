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
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class SymbolsSemanticTokensSupplierTest {

  @Autowired
  private SymbolsSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Autowired
  private SemanticTokensLegend legend;

  @Test
  void testMethodDeclaration() {
    // given
    String bsl = """
      Функция МояФункция()
        Возврат 1;
      КонецФункции
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    assertThat(decoded).isNotEmpty();

    int functionTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Function);
    var functionTokens = decoded.stream()
      .filter(t -> t.type() == functionTypeIdx)
      .toList();
    assertThat(functionTokens).hasSize(1);
  }

  @Test
  void testProcedureDeclaration() {
    // given
    String bsl = """
      Процедура МояПроцедура()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    assertThat(decoded).isNotEmpty();

    int methodTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
    var methodTokens = decoded.stream()
      .filter(t -> t.type() == methodTypeIdx)
      .toList();
    assertThat(methodTokens).hasSize(1);
  }

  @Test
  void testParameterDeclaration() {
    // given
    String bsl = """
      Функция Тест(Параметр1, Параметр2)
        Возврат Параметр1 + Параметр2;
      КонецФункции
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    assertThat(decoded).isNotEmpty();

    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    int definitionModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Definition);

    var parameterTokens = decoded.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
    // 2 definitions + 2 usages = 4 parameter tokens
    assertThat(parameterTokens).hasSizeGreaterThanOrEqualTo(2);

    // At least 2 should have Definition modifier (the declarations)
    var definitionTokens = parameterTokens.stream()
      .filter(t -> (t.modifiers() & definitionModifierMask) != 0)
      .toList();
    assertThat(definitionTokens).hasSize(2);
  }

  @Test
  void testVariableDeclaration() {
    // given
    String bsl = """
      Процедура Тест()
        Перем МояПеременная;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    assertThat(decoded).isNotEmpty();

    int variableTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);
    int definitionModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Definition);

    var variableTokens = decoded.stream()
      .filter(t -> t.type() == variableTypeIdx && (t.modifiers() & definitionModifierMask) != 0)
      .toList();
    assertThat(variableTokens).hasSize(1);
  }
}
