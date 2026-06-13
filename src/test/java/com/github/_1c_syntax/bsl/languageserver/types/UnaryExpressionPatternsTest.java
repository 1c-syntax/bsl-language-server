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
 * Унарные выражения: -/+/НЕ в разных позициях и комбинациях.
 */
@CleanupContextBeforeClassAndAfterClass
class UnaryExpressionPatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void simpleUnaryMinus() {
    var t = at("А = -10", "А = ".length());
    assertThat(qnames(t)).containsExactly("Число");
  }

  @Test
  void doubleUnaryMinus() {
    var t = at("Б = -(-20)", "Б = ".length());
    assertThat(qnames(t)).containsExactly("Число");
  }

  @Test
  void unaryMinusOnVariable() {
    var t = at("В = -А", "В = ".length());
    assertThat(qnames(t)).containsExactly("Число");
  }

  @Test
  void unaryMinusOnExpression() {
    var t = at("Г = -(А + Б)", "Г = ".length());
    assertThat(qnames(t)).containsExactly("Число");
  }

  @Test
  void simpleUnaryPlus() {
    var t = at("Д = +10", "Д = ".length());
    assertThat(qnames(t)).containsExactly("Число");
  }

  @Test
  void simpleNot() {
    var t = at("З = НЕ Истина", "З = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void notOnComparison() {
    var t = at("К = НЕ (10 > 5)", "К = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void notOnLogicalExpression() {
    var t = at("М = НЕ (Истина И Ложь)", "М = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void unaryMinusInBinop() {
    var t = at("Н = -10 + 20", "Н = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void notInTernary() {
    var t = at("Т = ?(НЕ Ложь, \"первая\", \"вторая\")", "Т = ".length());
    assertThat(qnames(t)).contains("Строка");
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
      "./src/test/resources/types/UnaryExpressionPatterns.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
