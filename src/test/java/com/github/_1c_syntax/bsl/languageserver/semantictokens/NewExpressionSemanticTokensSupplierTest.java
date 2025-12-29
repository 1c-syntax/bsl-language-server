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
class NewExpressionSemanticTokensSupplierTest {

  @Autowired
  private NewExpressionSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  @Test
  void testNewExpressionWithTypeName() {
    // given
    String bsl = """
      Процедура Тест()
        Массив = Новый Массив();
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int typeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    var typeTokens = tokens.stream()
      .filter(t -> t.type() == typeIdx)
      .toList();

    assertThat(typeTokens).hasSize(1);
    // "Массив" starts at column 16 (0-indexed: 16) and has length 6
    assertThat(typeTokens.get(0).line()).isEqualTo(1);
  }

  @Test
  void testMultipleNewExpressions() {
    // given
    String bsl = """
      Процедура Тест()
        Массив = Новый Массив();
        Список = Новый СписокЗначений();
        Структура = Новый Структура();
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int typeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    var typeTokens = tokens.stream()
      .filter(t -> t.type() == typeIdx)
      .toList();

    assertThat(typeTokens).hasSize(3);
  }

  @Test
  void testNewExpressionWithTypeMethod() {
    // given - New("TypeName") syntax should NOT produce tokens (no typeName() context)
    String bsl = """
      Процедура Тест()
        Объект = Новый("Массив");
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - this syntax uses Type() global method, not direct type name
    int typeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    var typeTokens = tokens.stream()
      .filter(t -> t.type() == typeIdx)
      .toList();

    assertThat(typeTokens).isEmpty();
  }

  @Test
  void testNewExpressionEnglish() {
    // given
    String bsl = """
      Procedure Test()
        Array = New Array();
      EndProcedure
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int typeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    var typeTokens = tokens.stream()
      .filter(t -> t.type() == typeIdx)
      .toList();

    assertThat(typeTokens).hasSize(1);
  }

  @Test
  void testNoTokensWithoutNewExpression() {
    // given
    String bsl = """
      Процедура Тест()
        А = 1;
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isEmpty();
  }
}
