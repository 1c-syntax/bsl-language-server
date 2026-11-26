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
 * Булевы выражения: базовые литералы, логические операции, цепочки,
 * сравнения, в условиях/циклах.
 */
@CleanupContextBeforeClassAndAfterClass
class BooleanExpressionsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void booleanTrue() {
    var t = at("А = Истина", "А = ".length());
    assertThat(qnames(t)).contains("Булево");
  }

  @Test
  void booleanFalse() {
    var t = at("Б = Ложь", "Б = ".length());
    assertThat(qnames(t)).contains("Булево");
  }

  @Test
  void logicalAndDoesNotCrash() {
    var t = at("АИБ = А И Б", "АИБ = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void logicalOrDoesNotCrash() {
    var t = at("АИЛИБ = А ИЛИ Б", "АИЛИБ = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void logicalNot() {
    var t = at("НА = НЕ А", "НА = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void complexChainExpressionDoesNotCrash() {
    var t = at("Цеп1 = А И Б ИЛИ НЕ А", "Цеп1 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void parenthesizedLogicalDoesNotCrash() {
    var t = at("Цеп2 = (А ИЛИ Б) И (НЕ А ИЛИ Б)", "Цеп2 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void equalityNumberDoesNotCrash() {
    var t = at("Срав1 = 1 = 1", "Срав1 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void inequalityNumberDoesNotCrash() {
    var t = at("Срав2 = 1 <> 2", "Срав2 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void equalityStringDoesNotCrash() {
    var t = at("Срав4 = \"a\" = \"a\"", "Срав4 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void equalityDateDoesNotCrash() {
    var t = at("Срав5 = '20200101' = '20200101'", "Срав5 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void complexBooleanExpressionDoesNotCrash() {
    var t = at("БулВыр = (НЕ Б) И (А ИЛИ (1 < 2))", "БулВыр = ".length());
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
      "./src/test/resources/types/BooleanExpressions.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
