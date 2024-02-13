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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.DefaultNodeEqualityComparer;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionParseTreeRewriter;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TransitiveOperationsIgnoringComparer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExpressionTreeComparersTest {

  @Test
  void strictEquality() {
    var code = "С = (А = 1) И (Б = 1) ИЛИ (А = 1) И (Б = 1)";
    var tree = (BinaryOperationNode) getExpressionTree(code);
    assertThat(tree.getOperator()).isEqualTo(BslOperator.OR);

    var comparer = new DefaultNodeEqualityComparer();
    assertThat(comparer.areEqual(tree.getLeft(), tree.getRight())).isTrue();

    var andExpr = (BinaryOperationNode) tree.getLeft();
    assertThat(comparer.areEqual(andExpr.getLeft(), andExpr.getRight())).isFalse();

  }

  @Test
  void transitiveEquality() {
    var code = "С = А + 2 = 2 + А";
    var tree = (BinaryOperationNode) getExpressionTree(code);
    assertThat(tree.getOperator()).isEqualTo(BslOperator.EQUAL);

    var defaultComparer = new DefaultNodeEqualityComparer();
    assertThat(defaultComparer.areEqual(tree.getLeft(), tree.getRight())).isFalse();

    var transitiveComparer = new TransitiveOperationsIgnoringComparer();
    assertThat(transitiveComparer.areEqual(tree.getLeft(), tree.getRight())).isTrue();

  }

  BSLParser.ExpressionContext parse(String code) {
    var dContext = TestUtils.getDocumentContext(code);
    return dContext.getAst().fileCodeBlock().codeBlock().statement(0).assignment().expression();
  }

  BslExpression getExpressionTree(String code) {
    var expression = parse(code);
    return ExpressionParseTreeRewriter.buildExpressionTree(expression);
  }

}
