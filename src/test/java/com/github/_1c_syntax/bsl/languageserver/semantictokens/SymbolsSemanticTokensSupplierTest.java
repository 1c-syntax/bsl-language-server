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
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class SymbolsSemanticTokensSupplierTest {

  @Autowired
  private SymbolsSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

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

    // then - function name should be highlighted as Function
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(0, 8, 10, SemanticTokenTypes.Function, "МояФункция")
    ));
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

    // then - procedure name should be highlighted as Method
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(0, 10, 12, SemanticTokenTypes.Method, "МояПроцедура")
    ));
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

    // then - parameter declarations should have Definition modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(0, 13, 9, SemanticTokenTypes.Parameter,
        Set.of(SemanticTokenModifiers.Definition), "Параметр1"),
      new ExpectedToken(0, 24, 9, SemanticTokenTypes.Parameter,
        Set.of(SemanticTokenModifiers.Definition), "Параметр2"),
      // Parameter usages in the body
      new ExpectedToken(1, 10, 9, SemanticTokenTypes.Parameter, "Параметр1"),
      new ExpectedToken(1, 22, 9, SemanticTokenTypes.Parameter, "Параметр2")
    ));
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

    // then - variable declaration should have Definition modifier
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 8, 13, SemanticTokenTypes.Variable,
        Set.of(SemanticTokenModifiers.Definition), "МояПеременная")
    ));
  }
}
