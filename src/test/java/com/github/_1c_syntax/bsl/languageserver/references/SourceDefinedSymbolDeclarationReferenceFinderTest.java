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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SourceDefinedSymbolDeclarationReferenceFinderTest {

  @Autowired
  private SourceDefinedSymbolDeclarationReferenceFinder referenceFinder;

  @Test
  void testFindReferenceOnMethodDeclaration() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/SourceDefinedSymbolDeclarationReferenceFinder.bsl");
    var module = documentContext.getSymbolTree().getModule();
    var method = documentContext.getSymbolTree().getMethods().get(0);

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(0, 15));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.getFrom()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol()).isEqualTo(method))
      .hasValueSatisfying(reference -> assertThat(reference.getSelectionRange()).isEqualTo(method.getSelectionRange()))
    ;
  }

  @Test
  void testCantFindReferenceOnMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/SourceDefinedSymbolDeclarationReferenceFinder.bsl");

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(2, 10));

    // then
    assertThat(optionalReference).isEmpty();
  }

}
