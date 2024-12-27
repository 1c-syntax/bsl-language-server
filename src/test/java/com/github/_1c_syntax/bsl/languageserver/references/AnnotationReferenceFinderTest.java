/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class AnnotationReferenceFinderTest extends AbstractServerContextAwareTest {

  @Autowired
  private AnnotationReferenceFinder referenceFinder;

  @Test
  void findReference() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.getFrom()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getName()).isEqualTo("ТестоваяАннотация"))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getSymbolKind()).isEqualTo(SymbolKind.TypeParameter))
      .hasValueSatisfying(reference -> assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(0, 0, 18)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(1, 10, 28)))
    ;
  }
}