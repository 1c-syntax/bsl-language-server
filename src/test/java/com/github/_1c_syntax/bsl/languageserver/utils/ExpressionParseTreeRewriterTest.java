/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
import com.github._1c_syntax.bsl.languageserver.utils.expressionTree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressionTree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressionTree.BslOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressionTree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressionTree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressionTree.ExpressionParseTreeRewriter;
import com.github._1c_syntax.bsl.languageserver.utils.expressionTree.TerminalSymbolNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExpressionParseTreeRewriterTest {

  @Test
  void SimpleBinaryOperationRewrite(){

    var expressionTree = getExpressionTree("А = 2 + 3;");
    assertThat(expressionTree instanceof BinaryOperationNode).isTrue();

    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getNodeType() == ExpressionNodeType.BINARY_OP).isTrue();
    assertThat(binary.getLeft().getNodeType() == ExpressionNodeType.LITERAL).isTrue();
    assertThat(binary.getRight().getNodeType() == ExpressionNodeType.LITERAL).isTrue();
    assertThat(binary.getOperator() == BslOperator.ADD).isTrue();

  }

  @Test
  void SimpleUnaryOperationRewrite(){

    var expressionTree = getExpressionTree("А = -2 + 3;");
    assertThat(expressionTree instanceof BinaryOperationNode).isTrue();

    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getLeft().getNodeType() == ExpressionNodeType.UNARY_OP).isTrue();
    assertThat(((BslOperationNode) binary.getLeft()).getOperator() == BslOperator.UNARY_MINUS).isTrue();
    assertThat(binary.getRight().getNodeType() == ExpressionNodeType.LITERAL).isTrue();
    assertThat(binary.getOperator() == BslOperator.ADD).isTrue();
  }

  @Test
  void BinaryExpressionsChain(){
    var code = "А = 2 + 2 + 3 - 1";
    var expressionTree = getExpressionTree(code);

    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getOperator() == BslOperator.SUBTRACT).isTrue();
    assertThat(binary.getLeft() instanceof BinaryOperationNode).isTrue();
    assertThat(binary.getRight().getNodeType() == ExpressionNodeType.LITERAL).isTrue();

  }

  @Test
  void BinaryExpressionsPriority(){

    // в конце
    var code = "А = 2 + 2 * 3";
    var expressionTree = getExpressionTree(code);

    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getOperator() == BslOperator.MULTIPLY).isTrue();
    assertThat(binary.getLeft() instanceof BinaryOperationNode).isTrue();
    assertThat(binary.getRight().getNodeType() == ExpressionNodeType.LITERAL).isTrue();

    // в начале
    code = "А = 2 * 2 + 3";
    expressionTree = getExpressionTree(code);
    binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getOperator() == BslOperator.MULTIPLY).isTrue();
    assertThat(binary.getLeft().getNodeType() == ExpressionNodeType.LITERAL).isTrue();
    assertThat(binary.getRight() instanceof BinaryOperationNode).isTrue();

  }

  BSLParser.ExpressionContext parse(String code){
    var dContext = TestUtils.getDocumentContext(code);
    return dContext.getAst().fileCodeBlock().codeBlock().statement(0).assignment().expression();
  }

  BslExpression getExpressionTree(String code){
    var expression = parse(code);
    var rewriter = new ExpressionParseTreeRewriter();
    expression.accept(rewriter);

    return rewriter.getExpressionTree();
  }

}