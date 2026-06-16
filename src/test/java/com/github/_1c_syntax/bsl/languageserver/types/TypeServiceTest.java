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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перенесённые из {@code feature/typeResolver} тесты — проверяют поведение
 * нового {@link TypeService} на тех же кейсах, что покрывал старый
 * {@code TypeResolver}.
 */
@SpringBootTest
class TypeServiceTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_FILE = "./src/test/resources/types/TypeResolver.os";

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUpWorkspaceContext() {
    initServerContext();
  }

  @Test
  void simpleType() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var types = typeService.expressionTypesAt(documentContext, new Position(5, 10));
    assertThat(types.refs()).hasSize(1);
  }

  @Test
  void twoTypesOnReassignment() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var types = typeService.expressionTypesAt(documentContext, new Position(8, 4));
    assertThat(types.refs()).hasSize(2);
  }

  @Test
  void twoTypesOnPlaceOfUsage() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var types = typeService.expressionTypesAt(documentContext, new Position(10, 10));
    assertThat(types.refs()).hasSize(2);
  }

  @Test
  void twoTypesFromSymbol() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var variableSymbol = documentContext.getSymbolTree()
      .getVariableSymbol("ДваТипа", documentContext.getSymbolTree().getModule()).orElseThrow();
    var reference = referenceOf(documentContext, variableSymbol);
    var types = typeService.typesAt(reference);
    assertThat(types.refs()).hasSize(2);
  }

  @Test
  void twoAssignmentsSameType() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var variableSymbol = documentContext.getSymbolTree()
      .getVariableSymbol("Переприсваивание", documentContext.getSymbolTree().getModule()).orElseThrow();
    var reference = referenceOf(documentContext, variableSymbol);
    var types = typeService.typesAt(reference);
    assertThat(types.refs()).hasSize(1);
  }

  @Test
  void newArray() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var variableSymbol = documentContext.getSymbolTree()
      .getVariableSymbol("ДругоеИмяМассива", documentContext.getSymbolTree().getModule()).orElseThrow();
    var reference = referenceOf(documentContext, variableSymbol);
    var types = typeService.typesAt(reference);
    assertThat(types.refs()).hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualToIgnoringCase("Массив");
  }

  @Test
  void globalMethodCall() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var variableSymbol = documentContext.getSymbolTree()
      .getVariableSymbol("РезультатФункции", documentContext.getSymbolTree().getModule()).orElseThrow();
    var reference = referenceOf(documentContext, variableSymbol);
    var types = typeService.typesAt(reference);
    assertThat(types.refs()).hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualToIgnoringCase("Строка");
  }

  private static Reference referenceOf(DocumentContext documentContext, VariableSymbol variableSymbol) {
    return Reference.of(
      documentContext.getSymbolTree().getModule(),
      variableSymbol,
      new Location(documentContext.getUri().toString(), variableSymbol.getSelectionRange()),
      OccurrenceType.DEFINITION
    );
  }

  @Test
  void definingUriEmptyForPlatformType() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var variableSymbol = documentContext.getSymbolTree()
      .getVariableSymbol("ДругоеИмяМассива", documentContext.getSymbolTree().getModule()).orElseThrow();
    var reference = referenceOf(documentContext, variableSymbol);
    var typeRef = typeService.typesAt(reference).refs().iterator().next();

    var uri = typeService.definingUri(typeRef);

    assertThat(uri).isEmpty();
  }

  @Test
  void definingUriEmptyForUnknownTypeRef() {
    var uri = typeService.definingUri(com.github._1c_syntax.bsl.languageserver.types.model.TypeRef.UNKNOWN);

    assertThat(uri).isEmpty();
  }

  @Test
  void definingUriEmptyForAnyTypeRef() {
    var uri = typeService.definingUri(com.github._1c_syntax.bsl.languageserver.types.model.TypeRef.ANY);

    assertThat(uri).isEmpty();
  }

  @Test
  void membersOfArrayResolved() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var variableSymbol = documentContext.getSymbolTree()
      .getVariableSymbol("ДругоеИмяМассива", documentContext.getSymbolTree().getModule()).orElseThrow();
    var reference = referenceOf(documentContext, variableSymbol);

    // when
    var types = typeService.typesAt(reference);
    var arrayRef = types.refs().iterator().next();
    var members = typeService.getMembers(arrayRef, documentContext.getFileType());

    // then
    assertThat(members).extracting(com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor::name)
      .contains("Добавить", "Количество", "Получить");
  }
}
