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
 * ExpressionTree паттерны: унарные, бинарные, тернарные, скобки,
 * длинные цепочки, конкатенация смешанных типов.
 */
@CleanupContextBeforeClassAndAfterClass
class ExpressionTreePatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void unaryMinus() {
    var t = at("А = -10", "А = ".length());
    assertThat(qnames(t)).containsExactly("Число");
  }

  @Test
  void unaryPlus() {
    var t = at("Б = +5", "Б = ".length());
    assertThat(qnames(t)).containsExactly("Число");
  }

  @Test
  void unaryNot() {
    var t = at("В = НЕ Истина", "В = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void doubleUnaryMinusInParens() {
    var t = at("Г = -(-7)", "Г = ".length());
    assertThat(qnames(t)).containsExactly("Число");
  }

  @Test
  void doubleNotDoesNotCrash() {
    var t = at("Д = НЕ НЕ Ложь", "Д = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void addition() {
    var t = at("Е = 10 + 5", "Е = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void subtraction() {
    var t = at("Ж = 10 - 5", "Ж = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void multiplication() {
    var t = at("З = 10 * 5", "З = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void division() {
    var t = at("И1 = 10 / 5", "И1 = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void modulo() {
    var t = at("К = 10 % 3", "К = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void comparisonGreater() {
    var t = at("Л = 10 > 5", "Л = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void comparisonNotEqual() {
    var t = at("Р = 10 <> 5", "Р = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void logicalAnd() {
    var t = at("С = Истина И Ложь", "С = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void logicalOr() {
    var t = at("Т = Истина ИЛИ Ложь", "Т = ".length());
    assertThat(qnames(t)).containsExactly("Булево");
  }

  @Test
  void ternaryNumbers() {
    var t = at("Ф = ?(Истина, 1, 2)", "Ф = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void ternaryStrings() {
    var t = at("Х = ?(10 > 5, \"большое\", \"малое\")", "Х = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void nestedTernary() {
    var t = at("Ц = ?(Ложь, ?(Истина, 1, 2), 3)", "Ц = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void stringConcatenation() {
    var t = at("Ч = \"a\" + \"b\"", "Ч = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void longArithmeticChainDoesNotCrash() {
    var t = at("Ы = 1 + 2 + 3 + 4 + 5", "Ы = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void parenthesized() {
    var t = at("Э = (1 + 2) * 3", "Э = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void deepNestedParens() {
    var t = at("Ю = ((1 + 2) * 3) / 2", "Ю = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void chainOfDereference() {
    var t = at("Глуб = Стр.X.Внутр", "Глуб = ".length());
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
      "./src/test/resources/types/ExpressionTreePatterns.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
