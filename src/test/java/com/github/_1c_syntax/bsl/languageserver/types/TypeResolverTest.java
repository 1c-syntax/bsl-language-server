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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TypeResolverTest {

  @Autowired
  private TypeResolver typeResolver;

  @Test
  void simpleType() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");

    // when
    var types = typeResolver.findTypes(documentContext.getUri(), new Position(5, 10));

    // then
    assertThat(types).hasSize(1);
  }

  @Test
  void twoTypesOnReassignment() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");

    // when
    var types = typeResolver.findTypes(documentContext.getUri(), new Position(8, 4));

    // then
    assertThat(types).hasSize(2);
  }

  @Test
  void twoTypesOnPlaceOfUsage() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");

    // when
    var types = typeResolver.findTypes(documentContext.getUri(), new Position(10, 10));

    // then
    assertThat(types).hasSize(2);
  }

  @Test
  void twoTypesFromSymbol() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");
    var variableSymbol = documentContext.getSymbolTree().getVariableSymbol("ДваТипа", documentContext.getSymbolTree().getModule()).orElseThrow();

    // when
    var types = typeResolver.findTypes(variableSymbol);

    // then
    assertThat(types).hasSize(2);
  }

  @Test
  void twoAssignments() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");
    var variableSymbol = documentContext.getSymbolTree().getVariableSymbol("Переприсваивание", documentContext.getSymbolTree().getModule()).orElseThrow();

    // when
    var types = typeResolver.findTypes(variableSymbol);

    // then
    assertThat(types).hasSize(1);
  }

  @Test
  void newArray() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");
    var variableSymbol = documentContext.getSymbolTree().getVariableSymbol("ДругоеИмяМассива", documentContext.getSymbolTree().getModule()).orElseThrow();

    // when
    var types = typeResolver.findTypes(variableSymbol);

    // then
    assertThat(types).hasSize(1);
    assertThat(types.get(0).getName()).isEqualTo("Массив");
  }

  @Test
  void globalMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");
    var variableSymbol = documentContext.getSymbolTree().getVariableSymbol("РезультатФункции", documentContext.getSymbolTree().getModule()).orElseThrow();

    // when
    var types = typeResolver.findTypes(variableSymbol);

    // then
    assertThat(types).hasSize(1);
    assertThat(types.get(0).getName()).isEqualTo("Строка");
  }

  @Test
  void varWithDescription() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeResolver.os");
    var variableSymbol = documentContext.getSymbolTree().getVariableSymbol("ПеременнаяСОписанием", documentContext.getSymbolTree().getModule()).orElseThrow();

    // when
    var types = typeResolver.findTypes(variableSymbol);

    // then
    assertThat(types).hasSize(1);
    assertThat(types.get(0).getName()).isEqualTo("Строка");
  }

}