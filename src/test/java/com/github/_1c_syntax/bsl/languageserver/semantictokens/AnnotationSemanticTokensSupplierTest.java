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

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class AnnotationSemanticTokensSupplierTest {

  @Autowired
  private AnnotationSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void testCompilerDirective() {
    // given
    String bsl = """
      &НаСервере
      Процедура Тест()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(0, 0, 10, SemanticTokenTypes.Decorator, "&НаСервере")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testAnnotationWithParams() {
    // given
    String bsl = """
      &Перед("ИмяМетода")
      Процедура Тест()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(0, 0, 6, SemanticTokenTypes.Decorator, "&Перед")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testAnnotationWithNamedParam() {
    // given
    String bsl = """
      &ИзменениеИКонтроль("ПередЗаписью", ИмяПараметра = "Значение")
      Процедура Тест()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(0, 0, 19, SemanticTokenTypes.Decorator, "&ИзменениеИКонтроль"),
      new ExpectedToken(0, 36, 12, SemanticTokenTypes.Parameter, "ИмяПараметра")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testMultipleAnnotations() {
    // given
    String bsl = """
      &НаСервере
      &Перед("ИмяМетода")
      Процедура Тест()
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(0, 0, 10, SemanticTokenTypes.Decorator, "&НаСервере"),
      new ExpectedToken(1, 0, 6, SemanticTokenTypes.Decorator, "&Перед")
    );
    helper.assertTokensMatch(decoded, expected);
  }
}
