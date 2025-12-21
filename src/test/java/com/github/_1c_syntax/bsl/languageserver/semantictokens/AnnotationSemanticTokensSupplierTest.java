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
class AnnotationSemanticTokensSupplierTest {

  @Autowired
  private AnnotationSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  @Test
  void testCompilerDirective() {
    // given
    String bsl = """
      &НаСервере
      Процедура Тест()
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isNotEmpty();

    int decoratorTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    var decoratorTokens = tokens.stream()
      .filter(t -> t.type() == decoratorTypeIdx)
      .toList();
    assertThat(decoratorTokens).hasSize(1);
  }

  @Test
  void testAnnotationWithParams() {
    // given
    String bsl = """
      &Перед("ИмяМетода")
      Процедура Тест()
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isNotEmpty();

    int decoratorTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    var decoratorTokens = tokens.stream()
      .filter(t -> t.type() == decoratorTypeIdx)
      .toList();
    assertThat(decoratorTokens).hasSize(1);
  }

  @Test
  void testAnnotationWithNamedParam() {
    // given
    String bsl = """
      &ИзменениеИКонтроль("ПередЗаписью", ИмяПараметра = "Значение")
      Процедура Тест()
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int decoratorTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);

    var decoratorTokens = tokens.stream()
      .filter(t -> t.type() == decoratorTypeIdx)
      .toList();
    assertThat(decoratorTokens).hasSize(1);

    var parameterTokens = tokens.stream()
      .filter(t -> t.type() == parameterTypeIdx)
      .toList();
    assertThat(parameterTokens).hasSize(1);
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

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int decoratorTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    var decoratorTokens = tokens.stream()
      .filter(t -> t.type() == decoratorTypeIdx)
      .toList();
    assertThat(decoratorTokens).hasSize(2);
  }
}

