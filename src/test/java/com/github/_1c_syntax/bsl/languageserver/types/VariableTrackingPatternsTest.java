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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Трекинг переменных: модульные/локальные, Перем-объявления, условные
 * присваивания, Try-Catch, переопределение типа.
 */
@CleanupContextBeforeClassAndAfterClass
class VariableTrackingPatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void localVariableNumber() {
    var t = at("Парам = 42", "Парам = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void localVariableString() {
    var t = at("Локал = \"строка\"", "Локал = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void variableDeclaredWithPerem() {
    var t = at("ОтлЛокал = 100", "ОтлЛокал = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void retypedVariableUnion() {
    // У = 1 потом У = "стало строкой" — union типов.
    var t = at("У = \"стало строкой\"", "У = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void conditionalAssignmentBranch1() {
    var t = at("Условн = \"ветка1\"", "Условн = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void conditionalAssignmentBranch2() {
    var t = at("Условн = 100", "Условн = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void tryCatchAssignmentInTryBranch() {
    var t = at("Х = 10", "Х = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void tryCatchAssignmentInCatchBranch() {
    var t = at("Х = \"ошибка\"", "Х = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void variableAssignedFromFunctionCall() {
    var t = at("ВызовФункции = СтрДлина(\"123\")", "ВызовФункции = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void variableUsedInBinaryExpression() {
    var t = at("НеЯвный = Парам + 10", "НеЯвный = ".length());
    assertThat(t).isNotNull();
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var dc = doc();
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(dc, new Position(line, charInLine + 1));
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/VariableTrackingPatterns.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
