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

import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndexFiller;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class SymbolsSemanticTokensSupplierTest {

  @Autowired
  private SymbolsSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;

  @Test
  void testMethodDeclaration() {
    // given
    String bsl = """
      Функция МояФункция()
        Возврат 1;
      КонецФункции
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isNotEmpty();

    // Should contain Function token for method name
    var functionTokens = tokens.stream()
      .filter(t -> t.type() == legend.getTokenTypes().indexOf(SemanticTokenTypes.Function))
      .toList();
    assertThat(functionTokens).isNotEmpty();
  }

  @Test
  void testProcedureDeclaration() {
    // given
    String bsl = """
      Процедура МояПроцедура()
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isNotEmpty();

    // Should contain Method token for procedure name
    var methodTokens = tokens.stream()
      .filter(t -> t.type() == legend.getTokenTypes().indexOf(SemanticTokenTypes.Method))
      .toList();
    assertThat(methodTokens).isNotEmpty();
  }

  @Test
  void testParameterDeclaration() {
    // given
    String bsl = """
      Функция Тест(Параметр1, Параметр2)
        Возврат Параметр1 + Параметр2;
      КонецФункции
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isNotEmpty();

    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    int definitionModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Definition);

    // Should contain Parameter tokens with Definition modifier
    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
    assertThat(parameterTokens).hasSizeGreaterThanOrEqualTo(2);

    // At least one should have Definition modifier (the declarations)
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

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isNotEmpty();

    int variableTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);
    int definitionModifierMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Definition);

    // Should contain Variable token with Definition modifier
    var variableTokens = tokens.stream()
      .filter(t -> t.type() == variableTypeIdx && (t.modifiers() & definitionModifierMask) != 0)
      .toList();
    assertThat(variableTokens).hasSize(1);
  }
}

