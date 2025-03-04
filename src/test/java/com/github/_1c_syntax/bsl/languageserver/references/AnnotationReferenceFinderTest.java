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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class AnnotationReferenceFinderTest extends AbstractServerContextAwareTest {

  @Autowired
  private AnnotationReferenceFinder referenceFinder;

  @Test
  void findReferenceOfAnnotation() {
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
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getSymbolKind()).isEqualTo(SymbolKind.Interface))
      .hasValueSatisfying(reference -> assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(0, 0, 76)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(7, 10, 28)))
    ;
  }

  @Test
  void findReferenceOfAnnotationParameterName() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(0, 25));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.getFrom()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getName()).isEqualTo("ВторойПараметр"))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getSymbolKind()).isEqualTo(SymbolKind.TypeParameter))
      .hasValueSatisfying(reference -> assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(0, 19, 33)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(7, 10, 28)))
    ;
  }

  @Test
  void findReferenceOfAnnotationParameterValue() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(0, 60));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.getFrom()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getName()).isEqualTo("Значение"))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getSymbolKind()).isEqualTo(SymbolKind.TypeParameter))
      .hasValueSatisfying(reference -> assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(0, 56, 75)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(7, 10, 28)))
    ;
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
     6, 24,  6, 19,  6, 27
     8, 10,  8,  4,  9, 12
     9, 10,  8,  4,  9, 12
    11, 19, 11, 19, 11, 21
    12, 19, 12, 19, 12, 22
    13, 19, 13, 19, 13, 25
    14, 19, 14, 19, 14, 29
    15, 19, 15, 19, 15, 31
    16, 19, 16, 19, 16, 23
    18,  4, 18,  4, 18,  6
    19,  4, 19,  4, 19,  7
    20,  4, 20,  4, 20, 12
    21,  4, 21,  4, 22, 12
    22,  4, 21,  4, 22, 12
    23,  4, 23,  4, 23, 10
    24,  4, 24,  4, 24, 14
    25,  4, 25,  4, 25, 16
    26,  4, 26,  4, 26,  8
    """
  )
  void findReferenceOfAnnotationParameterValue_allLiterals(int positionLine, int positionCharacter, int selectionRangeStartLine, int selectionRangeStartCharacter, int selectionRangeEndLine, int selectionRangeEndCharacter) {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(positionLine, positionCharacter));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.getFrom()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getName()).isEqualTo("Значение"))
      .hasValueSatisfying(reference -> assertThat(reference.getSymbol().getSymbolKind()).isEqualTo(SymbolKind.TypeParameter))
      .hasValueSatisfying(reference -> assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(selectionRangeStartLine, selectionRangeStartCharacter, selectionRangeEndLine, selectionRangeEndCharacter)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(7, 10, 28)))
    ;
  }
}