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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Расчёт по потоку в теле модуля: в OneScript-сценариях почти весь код живёт там,
 * а не в методах.
 */
@CleanupContextBeforeClassAndAfterClass
class ModuleBodyFlowTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUpWorkspaceContext() {
    initServerContext();
  }

  @Test
  void typeBeforeReassignmentIsTheFirstOne() {
    // given / when
    var types = at("ПослеЧисла = Значение", "ПослеЧисла = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void typeAfterReassignmentIsTheSecondOne() {
    // given / when
    var types = at("ПослеБулево = Значение", "ПослеБулево = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Булево");
  }

  @Test
  void structureFieldIsNotVisibleBeforeItsInsert() {
    // given / when
    var types = at("ПослеПервойВставки = Данные", "ПослеПервойВставки = ".length());

    // then
    assertThat(fieldNames(types)).containsExactly("Первое");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var documentContext = doc();
    var content = documentContext.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker;
    var lineStart = content.lastIndexOf('\n', targetOffset - 1) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, targetOffset - lineStart + 1));
  }

  private static DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/ModuleBodyFlow.os");
  }

  private static List<String> qnames(TypeSet types) {
    return types.refs().stream().map(ref -> ref.qualifiedName()).toList();
  }

  private static List<String> fieldNames(TypeSet types) {
    assertThat(types.refs()).hasSize(1);
    return List.copyOf(types.getLocalFields(types.refs().iterator().next()).keySet());
  }
}
