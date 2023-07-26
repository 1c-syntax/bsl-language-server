/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class SourceDefinedMethodCallInlayHintSupplierTest {

  private final static String FILE_PATH = "./src/test/resources/inlayhints/SourceDefinedMethodCallInlayHintSupplier.bsl";

  @Autowired
  private SourceDefinedMethodCallInlayHintSupplier supplier;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Test
  void testDefaultInlayHints() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().get(0);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = firstMethod.getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(2)
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("PlayersHealth:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 23));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **PlayersHealth**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Amount:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 32));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **Amount**: ");
      })
    ;
  }

  @Test
  void testInlayHintsShowParametersWithTheSameName() {

    // given
    configuration.getInlayHintOptions().getParameters().put(
      supplier.getId(),
      Either.forRight(Map.of("showParametersWithTheSameName", true))
    );

    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().get(0);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = firstMethod.getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(3)
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Player:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 14));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **Player**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("PlayersHealth:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 23));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **PlayersHealth**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Amount:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 32));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **Amount**: ");
      })
    ;
  }


}
