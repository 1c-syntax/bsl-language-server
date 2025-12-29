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
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class MethodCallSemanticTokensSupplierTest {

  @Autowired
  private MethodCallSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;

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

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int methodTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
    var methodTokens = tokens.stream()
      .filter(t -> t.type() == methodTypeIdx)
      .toList();
    // ВызываемаяПроцедура() call
    assertThat(methodTokens).hasSize(1);
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

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int methodTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
    var methodTokens = tokens.stream()
      .filter(t -> t.type() == methodTypeIdx)
      .toList();
    assertThat(methodTokens).hasSize(1);
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

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int methodTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
    var methodTokens = tokens.stream()
      .filter(t -> t.type() == methodTypeIdx)
      .toList();
    assertThat(methodTokens).hasSize(3);
  }

  @Test
  void testNoTokensForBuiltInMethods() {
    // given - builtin methods should not produce tokens from this supplier
    String bsl = """
      Процедура Тест()
        Сообщить("Привет");
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then - Builtin methods are not in the reference index
    assertThat(tokens).isEmpty();
  }
}

