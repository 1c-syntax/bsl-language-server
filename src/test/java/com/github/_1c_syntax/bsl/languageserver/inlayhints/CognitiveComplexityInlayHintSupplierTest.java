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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class CognitiveComplexityInlayHintSupplierTest {

  private final static String FILE_PATH = "./src/test/resources/inlayhints/CognitiveComplexityInlayHintSupplier.bsl";

  @Autowired
  private CognitiveComplexityInlayHintSupplier supplier;

  @Test
  void testGetInlayHints() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().get(0);
    var methodName = firstMethod.getName();

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = Ranges.create();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints).isEmpty();

    // when
    supplier.toggleHints(documentContext.getUri(), methodName);
    inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(1)
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("+1"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(1, 4));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
      });
  }

}