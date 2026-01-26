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
class NewExpressionSemanticTokensSupplierTest {

  @Autowired
  private NewExpressionSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void testNewExpressionWithTypeName() {
    // given
    String bsl = """
      Процедура Тест()
        Массив = Новый Массив();
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(1, 17, 6, SemanticTokenTypes.Type, "Массив")
    );

    helper.assertTokensMatch(decoded, expected);
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

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(1, 17, 6, SemanticTokenTypes.Type, "Массив"),
      new ExpectedToken(2, 17, 14, SemanticTokenTypes.Type, "СписокЗначений"),
      new ExpectedToken(3, 20, 9, SemanticTokenTypes.Type, "Структура")
    );

    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testNewExpressionWithTypeMethod() {
    // given - New("TypeName") syntax should NOT produce tokens (no typeName() context)
    String bsl = """
      Процедура Тест()
        Объект = Новый("Массив");
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - this syntax uses Type() global method, not direct type name
    assertThat(decoded).isEmpty();
  }

  @Test
  void testNewExpressionEnglish() {
    // given
    String bsl = """
      Procedure Test()
        Array = New Array();
      EndProcedure
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(1, 14, 5, SemanticTokenTypes.Type, "Array")
    );

    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testNoTokensWithoutNewExpression() {
    // given
    String bsl = """
      Процедура Тест()
        А = 1;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    assertThat(decoded).isEmpty();
  }
}
