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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.providers.InlayHintProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class VariableTypeInlayHintResolveTest extends AbstractServerContextAwareTest {

  private static final String FILE_PATH =
    "./src/test/resources/inlayhints/VariableTypeInlayHintSupplier.bsl";

  @Autowired
  private VariableTypeInlayHintSupplier supplier;

  @Autowired
  private InlayHintProvider provider;

  @Test
  void testTooltipIsResolvedLazily() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    var firstMethod = documentContext.getSymbolTree().getMethods().getFirst();
    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var params = new InlayHintParams(textDocumentIdentifier, firstMethod.getRange());

    var unresolved = supplier.getInlayHints(documentContext, params).getFirst();

    // then: жадно построенный хинт несёт data, но ещё без tooltip
    assertThat(unresolved.getTooltip()).isNull();
    assertThat(unresolved.getData()).isNotNull();

    // when: резолвим
    var data = provider.extractData(unresolved);
    var resolved = provider.resolveInlayHint(documentContext, unresolved, data);

    // then: tooltip заполнен описанием типа, data очищена
    assertThat(resolved.getTooltip()).isNotNull();
    assertThat(resolved.getTooltip().getRight().getValue()).contains("Массив");
    assertThat(resolved.getData()).isNull();
  }

  @Test
  void testResolveWithoutDataReturnsNullExtractedData() {

    // given
    var hintWithoutData = new InlayHint();
    hintWithoutData.setLabel(": Массив");

    // when
    var data = provider.extractData(hintWithoutData);

    // then
    // у хинта нет data — извлекать нечего, резолв не выполняется
    assertThat(data).isNull();
  }

  @Test
  void testResolveFallsBackToTypeNameForUnknownType() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    var hint = new InlayHint();
    hint.setLabel(": НесуществующийТип");
    var data = new VariableTypeInlayHintData(
      documentContext.getUri(), supplier.getId(), "НесуществующийТип");

    // when
    var resolved = supplier.resolve(documentContext, hint, data);

    // then
    // тип не восстанавливается реестром — tooltip строится по сохранённому имени
    assertThat(resolved.getTooltip()).isNotNull();
    assertThat(resolved.getTooltip().getRight().getValue()).contains("НесуществующийТип");
  }
}
