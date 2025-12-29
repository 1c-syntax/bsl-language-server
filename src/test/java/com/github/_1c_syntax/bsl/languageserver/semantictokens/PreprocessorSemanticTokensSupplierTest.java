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
class PreprocessorSemanticTokensSupplierTest {

  @Autowired
  private PreprocessorSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  @Test
  void testRegionDirective() {
    // given
    String bsl = """
      #Область МояОбласть
      Процедура Тест()
      КонецПроцедуры
      #КонецОбласти
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int namespaceTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    int variableTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);

    var namespaceTokens = tokens.stream()
      .filter(t -> t.type() == namespaceTypeIdx)
      .toList();
    // #Область and #КонецОбласти
    assertThat(namespaceTokens).hasSize(2);

    var variableTokens = tokens.stream()
      .filter(t -> t.type() == variableTypeIdx)
      .toList();
    // МояОбласть
    assertThat(variableTokens).hasSize(1);
  }

  @Test
  void testUseDirective() {
    // given
    String bsl = """
      #Использовать mylib
      Процедура Тест()
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int namespaceTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    int variableTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);

    var namespaceTokens = tokens.stream()
      .filter(t -> t.type() == namespaceTypeIdx)
      .toList();
    // #Использовать
    assertThat(namespaceTokens).hasSize(1);

    var variableTokens = tokens.stream()
      .filter(t -> t.type() == variableTypeIdx)
      .toList();
    // mylib
    assertThat(variableTokens).hasSize(1);
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int macroTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Macro);
    var macroTokens = tokens.stream()
      .filter(t -> t.type() == macroTypeIdx)
      .toList();
    // #Если, Сервер, Тогда, #КонецЕсли
    assertThat(macroTokens).hasSizeGreaterThanOrEqualTo(4);
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int macroTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Macro);
    var macroTokens = tokens.stream()
      .filter(t -> t.type() == macroTypeIdx)
      .toList();
    // #, native
    assertThat(macroTokens).hasSize(2);
  }
}

