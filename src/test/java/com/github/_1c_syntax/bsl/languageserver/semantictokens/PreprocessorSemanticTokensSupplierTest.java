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

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class PreprocessorSemanticTokensSupplierTest {

  @Autowired
  private PreprocessorSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void testRegionDirective() {
    // given
    String bsl = """
      #Область МояОбласть
      Процедура Тест()
      КонецПроцедуры
      #КонецОбласти
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(0, 0, 8, SemanticTokenTypes.Namespace, "#Область"),
      new ExpectedToken(0, 9, 10, SemanticTokenTypes.Variable, "МояОбласть"),
      new ExpectedToken(3, 0, 13, SemanticTokenTypes.Namespace, "#КонецОбласти")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testUseDirective() {
    // given
    String bsl = """
      #Использовать mylib
      Процедура Тест()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(0, 0, 13, SemanticTokenTypes.Namespace, "#Использовать"),
      new ExpectedToken(0, 14, 5, SemanticTokenTypes.Variable, "mylib")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testIfDirective() {
    // given
    String bsl = """
      #Если Сервер Тогда
      Процедура Тест()
      КонецПроцедуры
      #КонецЕсли
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - #Если and #КонецЕсли are single tokens, other keywords are separate
    var expected = List.of(
      new ExpectedToken(0, 0, 5, SemanticTokenTypes.Macro, "#Если"),
      new ExpectedToken(0, 6, 6, SemanticTokenTypes.Macro, "Сервер"),
      new ExpectedToken(0, 13, 5, SemanticTokenTypes.Macro, "Тогда"),
      new ExpectedToken(3, 0, 10, SemanticTokenTypes.Macro, "#КонецЕсли")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testNativeDirective() {
    // given
    String bsl = """
      #native
      Функция Тест()
        Возврат 1;
      КонецФункции
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    // #, native
    var expected = List.of(
      new ExpectedToken(0, 0, 1, SemanticTokenTypes.Macro, "#"),
      new ExpectedToken(0, 1, 6, SemanticTokenTypes.Macro, "native")
    );
    helper.assertTokensMatch(decoded, expected);
  }
}
