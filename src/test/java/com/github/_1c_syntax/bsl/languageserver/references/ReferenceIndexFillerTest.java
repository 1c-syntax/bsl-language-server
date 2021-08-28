/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReferenceIndexFillerTest {

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;
  @Autowired
  private ReferenceIndex referenceIndex;

  @Test
  void testFindCalledMethod() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/ReferenceIndexFillerTest.bsl");
    referenceIndexFiller.fill(documentContext);

    // when
    Optional<Reference> referencedSymbol = referenceIndex.getReference(documentContext.getUri(), new Position(4, 0));

    // then
    assertThat(referencedSymbol).isPresent();

    assertThat(referencedSymbol).get()
      .extracting(Reference::getSymbol)
      .extracting(Symbol::getName)
      .isEqualTo("Локальная");

    assertThat(referencedSymbol).get()
      .extracting(Reference::getSelectionRange)
      .isEqualTo(Ranges.create(4, 0, 4, 9));
  }

  @Test
  void testFindVariables() {
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexFillerVariableTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    var referencedSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(25, 24)
    );
    assertThat(referencedSymbol).isPresent();

    assertThat(referencedSymbol).get()
      .extracting(Reference::getSymbol)
      .extracting(Symbol::getName)
      .isEqualTo("Первая");

    assertThat(referencedSymbol).get()
      .extracting(Reference::getFrom)
      .extracting(Symbol::getName)
      .isEqualTo("ТретийМетод");

    assertThat(referencedSymbol).get()
      .extracting(Reference::isDefinition)
      .isEqualTo(false);

    var scopeMethod = documentContext
      .getSymbolTree()
      .getMethodSymbol("ТретийМетод");
    assertThat(scopeMethod).isPresent();
    var references = referenceIndex.getReferencesFrom(scopeMethod.get());
    assertThat(references).hasSize(11);

    var targetVariable = documentContext.getSymbolTree().getVariables().get(0);
    var usage = referenceIndex.getReferencesTo(targetVariable);
    assertThat(usage).hasSize(5);
  }

  @Test
  void testRebuildClearReferences() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/ReferenceIndexFillerTest.bsl");
    MethodSymbol methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Локальная").orElseThrow();

    // when
    referenceIndexFiller.fill(documentContext);
    List<Reference> referencesTo = referenceIndex.getReferencesTo(methodSymbol);

    // then
    assertThat(referencesTo).hasSize(1);

    // when
    // recalculate
    referenceIndexFiller.fill(documentContext);
    referencesTo = referenceIndex.getReferencesTo(methodSymbol);

    // then
    assertThat(referencesTo).hasSize(1);
  }
}