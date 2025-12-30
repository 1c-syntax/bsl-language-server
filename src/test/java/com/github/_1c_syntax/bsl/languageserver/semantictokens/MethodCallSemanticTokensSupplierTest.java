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
class MethodCallSemanticTokensSupplierTest {

  @Autowired
  private MethodCallSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void testMethodCall() {
    // given
    String bsl = """
      Процедура ВызываемаяПроцедура()
      КонецПроцедуры

      Процедура Тест()
        ВызываемаяПроцедура();
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(4, 2, 19, SemanticTokenTypes.Method, "ВызываемаяПроцедура")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testFunctionCall() {
    // given
    String bsl = """
      Функция ВызываемаяФункция()
        Возврат 1;
      КонецФункции

      Процедура Тест()
        Результат = ВызываемаяФункция();
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(5, 14, 17, SemanticTokenTypes.Method, "ВызываемаяФункция")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testMultipleMethodCalls() {
    // given
    String bsl = """
      Процедура Первая()
      КонецПроцедуры

      Процедура Вторая()
      КонецПроцедуры

      Процедура Тест()
        Первая();
        Вторая();
        Первая();
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier).stream().sorted().toList();

    // then
    var expected = List.of(
      new ExpectedToken(7, 2, 6, SemanticTokenTypes.Method, "Первая"),
      new ExpectedToken(8, 2, 6, SemanticTokenTypes.Method, "Вторая"),
      new ExpectedToken(9, 2, 6, SemanticTokenTypes.Method, "Первая")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testNoTokensForBuiltInMethods() {
    // given - builtin methods should not produce tokens from this supplier
    String bsl = """
      Процедура Тест()
        Сообщить("Привет");
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - Builtin methods are not in the reference index
    assertThat(decoded).isEmpty();
  }
}
