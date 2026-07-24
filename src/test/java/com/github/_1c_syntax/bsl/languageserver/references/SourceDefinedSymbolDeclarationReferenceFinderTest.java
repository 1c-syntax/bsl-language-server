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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

class SourceDefinedSymbolDeclarationReferenceFinderTest extends AbstractServerContextAwareTest {

  @Autowired
  private SourceDefinedSymbolDeclarationReferenceFinder referenceFinder;

  @BeforeEach
  void setUp() {
    initServerContext();
  }

  @Test
  void testFindReferenceOnMethodDeclaration() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/SourceDefinedSymbolDeclarationReferenceFinder.bsl");
    var module = documentContext.getSymbolTree().getModule();
    var method = documentContext.getSymbolTree().getMethods().getFirst();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(0, 15));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.from()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.symbol()).isEqualTo(method))
      .hasValueSatisfying(reference -> assertThat(reference.selectionRange()).isEqualTo(method.getSelectionRange()))
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

  @Test
  void bareAssignmentDeclaringPositionOfShadowedDynamicVariableIsFiltered() {
    // DYNAMIC-переменная (без Перем), перекрытая одноимённым self-свойством —
    // в своей же "объявляющей" позиции (первое присваивание) finder уступает
    // дальше по цепочке (в итоге — PlatformMemberReferenceFinder).
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();
    var content = """
      Процедура Тест()
        Реквизит1 = "А";
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(uri, content, context);

    var reference = referenceFinder.findReference(documentContext.getUri(), new Position(1, 3));

    assertThat(reference).isEmpty();
  }

  @Test
  void bareAssignmentDeclaringPositionOfUnshadowedDynamicVariableResolvesNormally() {
    // Обычная DYNAMIC-переменная (без self-члена того же имени) — резолвится
    // как обычно, не отфильтровывается.
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();
    var content = """
      Процедура Тест()
        МояЛокальная = "А";
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(uri, content, context);

    var reference = referenceFinder.findReference(documentContext.getUri(), new Position(1, 3));

    assertThat(reference)
      .isPresent()
      .hasValueSatisfying(ref -> assertThat(ref.symbol()).isInstanceOfSatisfying(VariableSymbol.class,
        variable -> assertThat(variable.getKind()).isEqualTo(VariableKind.DYNAMIC)));
  }

}
