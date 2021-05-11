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
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ConstructorCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionParseTreeRewriter;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.SkippedCallArgumentNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExpressionParseTreeRewriterTest {

  @Test
  void simpleBinaryOperationRewrite(){

    var expressionTree = getExpressionTree("А = 2 + 3;");
    assertThat(expressionTree instanceof BinaryOperationNode).isTrue();

    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getNodeType() == ExpressionNodeType.BINARY_OP).isTrue();
    assertThat(binary.getLeft().getNodeType() == ExpressionNodeType.LITERAL).isTrue();
    assertThat(binary.getRight().getNodeType() == ExpressionNodeType.LITERAL).isTrue();
    assertThat(binary.getOperator() == BslOperator.ADD).isTrue();

  }

  @Test
  void simpleUnaryOperationRewrite(){

    var expressionTree = getExpressionTree("А = -2 + 3;");
    assertThat(expressionTree instanceof BinaryOperationNode).isTrue();

    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getLeft().getNodeType() == ExpressionNodeType.UNARY_OP).isTrue();
    assertThat(((BslOperationNode) binary.getLeft()).getOperator() == BslOperator.UNARY_MINUS).isTrue();
    assertThat(binary.getRight().getNodeType() == ExpressionNodeType.LITERAL).isTrue();
    assertThat(binary.getOperator() == BslOperator.ADD).isTrue();
  }

  @Test
  void binaryExpressionsChain(){
    var code = "А = 2 + 2 + 3 - 1";
    var expressionTree = getExpressionTree(code);

    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getOperator() == BslOperator.SUBTRACT).isTrue();
    assertThat(binary.getLeft() instanceof BinaryOperationNode).isTrue();
    assertThat(binary.getRight().getNodeType() == ExpressionNodeType.LITERAL).isTrue();

  }

  @Test
  void binaryExpressionsPriority(){

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

  @Test
  public void booleanAndArithmeticPriority(){
    var code = "Рез = А > 2 И Б + 3 < 0";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode)expressionTree;

    assertThat(binary.getOperator()).isEqualTo(BslOperator.AND);
    assertThat(((BinaryOperationNode)binary.getLeft()).getOperator()).isEqualTo(BslOperator.GREATER);
    assertThat(((BinaryOperationNode)binary.getRight()).getOperator()).isEqualTo(BslOperator.LESS);

    var additionOnLeft = ((BinaryOperationNode)binary.getRight()).getLeft();
    assertThat(additionOnLeft.getNodeType() == ExpressionNodeType.BINARY_OP).isTrue();

  }

  @Test
  public void booleanPriority(){
    var code = "Рез = А или Б и не В";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode)expressionTree;

    assertThat(binary.getOperator()).isEqualTo(BslOperator.OR);
    assertThat((binary.getLeft()).getNodeType()).isEqualTo(ExpressionNodeType.IDENTIFIER);
    assertThat(((BinaryOperationNode)binary.getRight()).getOperator()).isEqualTo(BslOperator.AND);

    var negation = ((BinaryOperationNode)binary.getRight()).getRight();
    assertThat(negation.getNodeType() == ExpressionNodeType.UNARY_OP).isTrue();
    assertThat(((UnaryOperationNode)negation).getOperator() == BslOperator.NOT).isTrue();

  }

  @Test
  public void dereferenceOfProperty(){
    var code = "Рез = Структура.Свойство";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
  }

  @Test
  public void dereferenceOfMethod(){
    var code = "Рез = Структура.Метод()";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
    assertThat(binary.getRight().getNodeType()).isEqualTo(ExpressionNodeType.CALL);
  }

  @Test
  public void indexAccessToVariable(){
    var code = "Рез = Массив[10]";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode)expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.INDEX_ACCESS);
  }

  @Test
  public void chainedModifiers(){
    var code = "Рез = Структура.Массив[10 + 2].Свойство1.Свойство2[Структура.Свойство3]";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode)expressionTree;

    // should be:
    // INDEX_ACCESS(N, DEREFERENCE(Структура.Свойство3))
    // N:= DEREFERENCE(M, Свойство2)
    // M:= DEREFERENCE(INDEX_ACCESS(L, 10+2), Свойство1)
    // L:= DEREFERENCE(Структура, Массив)

    assertThat(binary.getOperator()).isEqualTo(BslOperator.INDEX_ACCESS);

    var indexArgument = (BinaryOperationNode)binary.getRight();
    assertThat(indexArgument.getRight().getRepresentingAst().getText()).isEqualTo("Свойство3");
    assertThat(indexArgument.getLeft().getRepresentingAst().getText()).isEqualTo("Структура");

    var N = (BinaryOperationNode)binary.getLeft();
    assertThat(N.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
    assertThat(N.getRight().getRepresentingAst().getText()).isEqualTo("Свойство2");

    var M = (BinaryOperationNode)N.getLeft();
    assertThat(M.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
    assertThat(M.getRight().getRepresentingAst().getText()).isEqualTo("Свойство1");

    var leftOfM = (BinaryOperationNode)M.getLeft();
    assertThat(leftOfM.getOperator()).isEqualTo(BslOperator.INDEX_ACCESS);

    var L = (BinaryOperationNode)leftOfM.getLeft();
    assertThat(L.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
    assertThat(L.getLeft().getRepresentingAst().getText()).isEqualTo("Структура");
    assertThat(L.getRight().getRepresentingAst().getText()).isEqualTo("Массив");

  }

  @Test
  public void canBuildGlobalCall(){
    var code = "Рез = Метод(1,,3)";
    var expressionTree = getExpressionTree(code);

    var call = (MethodCallNode)expressionTree;
    assertThat(call.getName().getText()).isEqualTo("Метод");
    assertThat(call.arguments()).hasSize(3);
    assertThat(call.arguments().get(1)).isExactlyInstanceOf(SkippedCallArgumentNode.class);
  }

  @Test
  public void canBuildGlobalCallWithModifiers(){
    var code = "Рез = Метод(1,,3).Свойство";
    var expressionTree = getExpressionTree(code);

    var deref = (BinaryOperationNode)expressionTree;
    var call = (MethodCallNode)deref.getLeft();
    assertThat(call.getName().getText()).isEqualTo("Метод");
    assertThat(call.arguments()).hasSize(3);
    assertThat(call.arguments().get(1)).isExactlyInstanceOf(SkippedCallArgumentNode.class);
  }

  @Test
  public void canCallDynamicConstructor(){
    var code = "Рез = Новый(ПеремИмяТипа, Арг)";
    var constructor = (ConstructorCallNode)getExpressionTree(code);

    assertThat(constructor.isStaticallyTyped()).isFalse();
    assertThat(constructor.getTypeName().getNodeType()).isEqualTo(ExpressionNodeType.IDENTIFIER);
    assertThat(constructor.arguments()).hasSize(1);

  }

  @Test
  public void canCallStaticConstructor(){
    var code = "Рез = Новый ИмяТипа(Арг)";
    var constructor = (ConstructorCallNode)getExpressionTree(code);

    assertThat(constructor.isStaticallyTyped()).isTrue();
    assertThat(constructor.getTypeName().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(constructor.arguments()).hasSize(1);

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