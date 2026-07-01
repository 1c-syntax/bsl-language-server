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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.providers.InlayHintProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты единого {@link MethodCallInlayHintSupplier}: проверяют, что
 * под одним ключом {@code methodCall} собираются подсказки и source-defined, и
 * платформенных вызовов, а ссылка на объявление параметра у source-defined вызова
 * сохраняется.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class MethodCallInlayHintSupplierTest extends AbstractServerContextAwareTest {

  private static final String SOURCE_DEFINED_FILE =
    "./src/test/resources/inlayhints/SourceDefinedMethodCallInlayHintSupplier.bsl";

  @Autowired
  private MethodCallInlayHintSupplier supplier;

  @Autowired
  private InlayHintProvider provider;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Test
  void idIsUnifiedMethodCallKey() {
    assertThat(supplier.getId()).isEqualTo("methodCall");
  }

  @Test
  void sourceDefinedParameterHintCarriesLinkThroughUnifiedSupplier() {
    // given — объявление параметра PlayersHealth известно на этапе построения хинта.
    var documentContext = TestUtils.getDocumentContextFromFile(SOURCE_DEFINED_FILE);
    var changeHealth = documentContext.getSymbolTree().getMethods().stream()
      .filter(method -> "ChangeHealth".equalsIgnoreCase(method.getName()))
      .findFirst()
      .orElseThrow();
    var playersHealthParameter = changeHealth.getParameters().get(1);

    var params = new InlayHintParams(
      TestUtils.getTextDocumentIdentifier(documentContext.getUri()),
      documentContext.getSymbolTree().getMethods().getFirst().getRange()
    );

    // when
    List<InlayHint> hints = supplier.getInlayHints(documentContext, params);

    // then — у части метки PlayersHealth ссылка указывает на диапазон объявления
    // параметра в сигнатуре вызываемого метода (ссылка переживает объединение сапплаеров).
    assertThat(hints)
      .filteredOn(hint -> "PlayersHealth:".equals(labelValue(hint)))
      .singleElement()
      .satisfies(hint -> {
        InlayHintLabelPart labelPart = hint.getLabel().getRight().getFirst();
        assertThat(labelPart.getLocation()).isNotNull();
        assertThat(labelPart.getLocation().getUri()).isEqualTo(documentContext.getUri().toString());
        assertThat(labelPart.getLocation().getRange()).isEqualTo(playersHealthParameter.getRange());
      });
  }

  @Test
  void methodCallFalseDisablesParameterHints() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(SOURCE_DEFINED_FILE);
    var params = new InlayHintParams(
      TestUtils.getTextDocumentIdentifier(documentContext.getUri()),
      documentContext.getSymbolTree().getMethods().getFirst().getRange()
    );

    // when — по умолчанию подсказки параметров есть.
    var enabledHints = provider.getInlayHint(documentContext, params);

    // then
    assertThat(enabledHints).anyMatch(hint -> hint.getKind() == InlayHintKind.Parameter);

    // when — methodCall: false гасит единственный сапплаер вызовов методов.
    configuration.getInlayHintOptions().getParameters().put("methodCall", Either.forLeft(false));
    var disabledHints = provider.getInlayHint(documentContext, params);

    // then — подсказок-параметров не остаётся.
    assertThat(disabledHints).noneMatch(hint -> hint.getKind() == InlayHintKind.Parameter);
  }

  private static String labelValue(InlayHint inlayHint) {
    return inlayHint.getLabel().getRight().getFirst().getValue();
  }
}
