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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты на вывод типов через цепочки аксессоров: {@code A().B()}, {@code A().B},
 * где промежуточные узлы — результаты вызовов методов, чьих собственных
 * {@code Symbol} в {@code SymbolTree} нет.
 */
@SpringBootTest
class ChainedAccessorInferenceTest extends AbstractServerContextAwareTest {

  private static final String PATH = "./src/test/resources/types/ChainedAccessors.os";

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUpWorkspaceContext() {
    initServerContext();
  }

  @Test
  void inferNumberFromArrayCount() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH);
    var variable = lookup(documentContext, "КоличествоЭлементов");

    var types = typeService.typesAt(referenceOf(documentContext, variable));

    assertThat(types.refs()).hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualToIgnoringCase("Число");
  }

  @Test
  void inferNumberFromStructureCount() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH);
    var variable = lookup(documentContext, "КоличествоПолей");

    var types = typeService.typesAt(referenceOf(documentContext, variable));

    assertThat(types.refs()).hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualToIgnoringCase("Число");
  }

  @Test
  void unknownMemberReturnsEmpty() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH);
    var variable = lookup(documentContext, "ВложенноеКоличество");

    var types = typeService.typesAt(referenceOf(documentContext, variable));

    // Массив.Получить(0) объявлен с return type Произвольный (из oscript stdlib),
    // который канонизируется в TypeRef.ANY (qualifiedName "Any").
    assertThat(types.refs()).hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualToIgnoringCase("Any");
  }

  private static VariableSymbol lookup(DocumentContext documentContext, String name) {
    return documentContext.getSymbolTree()
      .getVariableSymbol(name, documentContext.getSymbolTree().getModule())
      .orElseThrow(() -> new AssertionError("variable not found: " + name));
  }

  private static Reference referenceOf(DocumentContext documentContext, VariableSymbol variableSymbol) {
    return Reference.of(
      documentContext.getSymbolTree().getModule(),
      variableSymbol,
      new Location(documentContext.getUri().toString(), variableSymbol.getSelectionRange()),
      OccurrenceType.DEFINITION
    );
  }
}
