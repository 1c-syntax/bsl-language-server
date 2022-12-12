/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorConstraints;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ConstructorCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionParseTreeRewriter;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.PreprocessorSymbolNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.SkippedCallArgumentNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExpressionParseTreeRewriterTest {

  @Test
  void simpleBinaryOperationRewrite() {

    var expressionTree = getExpressionTree("А = 2 + 3;");
    assertThat(expressionTree).isInstanceOf(BinaryOperationNode.class);

    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getNodeType()).isEqualTo(ExpressionNodeType.BINARY_OP);
    assertThat(binary.getLeft().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getRight().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getOperator()).isEqualTo(BslOperator.ADD);

  }

  @Test
  void simpleUnaryOperationRewrite() {

    var expressionTree = getExpressionTree("А = -2 + 3;");
    assertThat(expressionTree).isInstanceOf(BinaryOperationNode.class);

    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getLeft().getNodeType()).isEqualTo(ExpressionNodeType.UNARY_OP);
    assertThat(((BslOperationNode) binary.getLeft()).getOperator()).isEqualTo(BslOperator.UNARY_MINUS);
    assertThat(binary.getRight().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getOperator()).isEqualTo(BslOperator.ADD);
  }

  @Test
  void binaryExpressionsChain() {
    var code = "А = 2 + 2 + 3 - 1";
    var expressionTree = getExpressionTree(code);

    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.ADD);
    assertThat(binary.getLeft().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getRight()).isInstanceOf(BinaryOperationNode.class);

    binary = (BinaryOperationNode) binary.getRight();
    assertThat(binary.getOperator()).isEqualTo(BslOperator.ADD);
    assertThat(binary.getLeft().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getRight()).isInstanceOf(BinaryOperationNode.class);

    binary = (BinaryOperationNode) binary.getRight();
    assertThat(binary.getOperator()).isEqualTo(BslOperator.SUBTRACT);
    assertThat(binary.getLeft().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getRight().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);


  }

  @Test
  void binaryExpressionsPriority() {

    // в конце
    var code = "А = 2 + 2 * 3";
    var expressionTree = getExpressionTree(code);

    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.ADD);
    assertThat(binary.getLeft().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getRight()).isInstanceOf(BinaryOperationNode.class);
    assertThat(((BinaryOperationNode) binary.getRight()).getOperator()).isEqualTo(BslOperator.MULTIPLY);

    // в начале
    code = "А = 2 * 2 + 3";
    expressionTree = getExpressionTree(code);
    binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.ADD);
    assertThat(binary.getLeft()).isInstanceOf(BinaryOperationNode.class);
    assertThat(((BinaryOperationNode) binary.getLeft()).getOperator()).isEqualTo(BslOperator.MULTIPLY);
    assertThat(binary.getRight().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);

  }

  @Test
  void booleanAndArithmeticPriority() {
    var code = "Рез = А > 2 И Б + 3 < 0";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;

    assertThat(binary.getOperator()).isEqualTo(BslOperator.AND);
    assertThat(((BinaryOperationNode) binary.getLeft()).getOperator()).isEqualTo(BslOperator.GREATER);
    assertThat(((BinaryOperationNode) binary.getRight()).getOperator()).isEqualTo(BslOperator.LESS);

    var additionOnLeft = ((BinaryOperationNode) binary.getRight()).getLeft();
    assertThat(additionOnLeft.getNodeType()).isEqualTo(ExpressionNodeType.BINARY_OP);

  }

  @Test
  void booleanPriority() {
    var code = "Рез = А или Б и не В";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;

    assertThat(binary.getOperator()).isEqualTo(BslOperator.OR);
    assertThat((binary.getLeft()).getNodeType()).isEqualTo(ExpressionNodeType.IDENTIFIER);
    assertThat(((BinaryOperationNode) binary.getRight()).getOperator()).isEqualTo(BslOperator.AND);

    var negation = ((BinaryOperationNode) binary.getRight()).getRight();
    assertThat(negation.getNodeType()).isEqualTo(ExpressionNodeType.UNARY_OP);
    assertThat(((UnaryOperationNode) negation).getOperator()).isEqualTo(BslOperator.NOT);

  }

  @Test
  void dereferenceOfProperty() {
    var code = "Рез = Структура.Свойство";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
  }

  @Test
  void dereferenceOfMethod() {
    var code = "Рез = Структура.Метод()";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
    assertThat(binary.getRight().getNodeType()).isEqualTo(ExpressionNodeType.CALL);
  }

  @Test
  void indexAccessToVariable() {
    var code = "Рез = Массив[10]";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.INDEX_ACCESS);
  }

  @Test
  void chainedModifiers() {
    var code = "Рез = Структура.Массив[10 + 2].Свойство1.Свойство2[Структура.Свойство3]";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;

    // should be:
    // INDEX_ACCESS(N, DEREFERENCE(Структура.Свойство3))
    // N:= DEREFERENCE(M, Свойство2)
    // M:= DEREFERENCE(INDEX_ACCESS(L, 10+2), Свойство1)
    // L:= DEREFERENCE(Структура, Массив)

    assertThat(binary.getOperator()).isEqualTo(BslOperator.INDEX_ACCESS);

    var indexArgument = (BinaryOperationNode) binary.getRight();
    assertThat(indexArgument.getRight().getRepresentingAst().getText()).isEqualTo("Свойство3");
    assertThat(indexArgument.getLeft().getRepresentingAst().getText()).isEqualTo("Структура");

    var N = (BinaryOperationNode) binary.getLeft();
    assertThat(N.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
    assertThat(N.getRight().getRepresentingAst().getText()).isEqualTo("Свойство2");

    var M = (BinaryOperationNode) N.getLeft();
    assertThat(M.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
    assertThat(M.getRight().getRepresentingAst().getText()).isEqualTo("Свойство1");

    var leftOfM = (BinaryOperationNode) M.getLeft();
    assertThat(leftOfM.getOperator()).isEqualTo(BslOperator.INDEX_ACCESS);

    var L = (BinaryOperationNode) leftOfM.getLeft();
    assertThat(L.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
    assertThat(L.getLeft().getRepresentingAst().getText()).isEqualTo("Структура");
    assertThat(L.getRight().getRepresentingAst().getText()).isEqualTo("Массив");

  }

  @Test
  void canBuildGlobalCall() {
    var code = "Рез = Метод(1,,3)";
    var expressionTree = getExpressionTree(code);

    var call = (MethodCallNode) expressionTree;
    assertThat(call.getName().getText()).isEqualTo("Метод");
    assertThat(call.arguments()).hasSize(3);
    assertThat(call.arguments().get(1)).isExactlyInstanceOf(SkippedCallArgumentNode.class);
  }

  @Test
  void canBuildGlobalCallInExpression() {
    var code = "Рез = 2 * Метод(2+3)";
    var expressionTree = getExpressionTree(code);

    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.MULTIPLY);
    assertThat(binary.getRight()).isInstanceOf(MethodCallNode.class);

    var call = (MethodCallNode) binary.getRight();
    assertThat(call.arguments().get(0)).isInstanceOf(BinaryOperationNode.class);
  }

  @Test
  void canBuildGlobalCallWithModifiers() {
    var code = "Рез = Метод(1,,3).Свойство";
    var expressionTree = getExpressionTree(code);

    var deref = (BinaryOperationNode) expressionTree;
    var call = (MethodCallNode) deref.getLeft();
    assertThat(call.getName().getText()).isEqualTo("Метод");
    assertThat(call.arguments()).hasSize(3);
    assertThat(call.arguments().get(1)).isExactlyInstanceOf(SkippedCallArgumentNode.class);
  }

  @Test
  void canCallDynamicConstructor() {
    var code = "Рез = Новый(ПеремИмяТипа, Арг)";
    var constructor = (ConstructorCallNode) getExpressionTree(code);

    assertThat(constructor.isStaticallyTyped()).isFalse();
    assertThat(constructor.getTypeName().getNodeType()).isEqualTo(ExpressionNodeType.IDENTIFIER);
    assertThat(constructor.arguments()).hasSize(1);

  }

  @Test
  void canCallStaticConstructor() {
    var code = "Рез = Новый ИмяТипа(Арг)";
    var constructor = (ConstructorCallNode) getExpressionTree(code);

    assertThat(constructor.isStaticallyTyped()).isTrue();
    assertThat(constructor.getTypeName().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(constructor.arguments()).hasSize(1);

  }

  @Test
  void canProcessParenthesisPriority() {
    var code = "А = 2 * (2 + 3)";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.MULTIPLY);
    assertThat(binary.getLeft().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getRight()).isInstanceOf(BinaryOperationNode.class);
  }

  @Test
  void canProcessModifiersAfterParenthesis() {
    var code = "А = 2 * (ВСкобках()).Свойство";
    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.MULTIPLY);
    assertThat(binary.getLeft().getNodeType()).isEqualTo(ExpressionNodeType.LITERAL);
    assertThat(binary.getRight()).isInstanceOf(BinaryOperationNode.class);

    binary = (BinaryOperationNode) binary.getRight();
    assertThat(binary.getOperator()).isEqualTo(BslOperator.DEREFERENCE);
  }

  BSLParser.ExpressionContext parse(String code) {
    var dContext = TestUtils.getDocumentContext(code);
    return dContext.getAst().fileCodeBlock().codeBlock().statement(0).assignment().expression();
  }

  @Test
  void realLifeHardExpression() {
    var code = "СодержитПоля = ВложенныеЭлементы.Количество() > 0\n" +
      "И Не (ВложенныеЭлементы.Количество() = 1\n" +
      "И ТипЗнч(ВложенныеЭлементы[0]) = Тип(\"АвтоВыбранноеПолеКомпоновкиДанных\"));";

    var expressionTree = getExpressionTree(code);
    var binary = (BinaryOperationNode) expressionTree;
    assertThat(binary.getOperator()).isEqualTo(BslOperator.AND);
    assertThat(binary.getLeft()).isInstanceOf(BinaryOperationNode.class);
    assertThat(((BinaryOperationNode) binary.getLeft()).getOperator()).isEqualTo(BslOperator.GREATER);

    assertThat(binary.getRight()).isInstanceOf(UnaryOperationNode.class);
    var not = (UnaryOperationNode) binary.getRight();
    assertThat(not.getOperator()).isEqualTo(BslOperator.NOT);
    assertThat(not.getOperand()).isInstanceOf(BinaryOperationNode.class);

    binary = (BinaryOperationNode) not.getOperand();
    assertThat(binary.getOperator()).isEqualTo(BslOperator.AND);
    assertThat(binary.getLeft().<BinaryOperationNode>cast().getOperator()).isEqualTo(BslOperator.EQUAL);
    assertThat(binary.getRight().<BinaryOperationNode>cast().getOperator()).isEqualTo(BslOperator.EQUAL);
  }

  @Test
  void preprocessorUno() {
    var variants = Map.ofEntries(
      Map.entry("Клиент", PreprocessorConstraints.CLIENT),
      Map.entry("Client", PreprocessorConstraints.CLIENT),
      Map.entry("НаКлиенте", PreprocessorConstraints.CLIENT),
      Map.entry("AtClient", PreprocessorConstraints.CLIENT),
      Map.entry("НаСервере", PreprocessorConstraints.SERVER),
      Map.entry("AtServer", PreprocessorConstraints.SERVER),
      Map.entry("Сервер", PreprocessorConstraints.SERVER),
      Map.entry("Server", PreprocessorConstraints.SERVER),
      Map.entry("ТонкийКлиент", PreprocessorConstraints.THIN_CLIENT),
      Map.entry("ThinClient", PreprocessorConstraints.THIN_CLIENT),
      Map.entry("ВебКлиент", PreprocessorConstraints.WEB_CLIENT),
      Map.entry("WebClient", PreprocessorConstraints.WEB_CLIENT),
      Map.entry("МобильныйАвтономныйСервер", PreprocessorConstraints.MOBILE_STANDALONE_SERVER),
      Map.entry("MobileStandaloneServer", PreprocessorConstraints.MOBILE_STANDALONE_SERVER),
      Map.entry("МобильноеПриложениеКлиент", PreprocessorConstraints.MOBILE_APP_CLIENT),
      Map.entry("MobileAppClient", PreprocessorConstraints.MOBILE_APP_CLIENT),
      Map.entry("МобильноеПриложениеСервер", PreprocessorConstraints.MOBILE_APP_SERVER),
      Map.entry("MobileAppServer", PreprocessorConstraints.MOBILE_APP_SERVER),
      Map.entry("МобильныйКлиент", PreprocessorConstraints.MOBILE_CLIENT),
      Map.entry("MobileClient", PreprocessorConstraints.MOBILE_CLIENT),
      Map.entry("ТолстыйКлиентОбычноеПриложение", PreprocessorConstraints.ORDINARY_THICK_CLIENT),
      Map.entry("ThickClientOrdinaryApplication", PreprocessorConstraints.ORDINARY_THICK_CLIENT),
      Map.entry("ТолстыйКлиентУправляемоеПриложение", PreprocessorConstraints.MANAGED_THICK_CLIENT),
      Map.entry("ThickClientManagedApplication", PreprocessorConstraints.MANAGED_THICK_CLIENT),
      Map.entry("ВнешнееСоединение", PreprocessorConstraints.EXTERNAL_CONNECTION),
      Map.entry("ExternalConnection", PreprocessorConstraints.EXTERNAL_CONNECTION));

    for (var variant : variants.entrySet()) {
      var expression = getPreprocessorExpressionTree(variant.getKey());
      assertThat(expression).isInstanceOf(PreprocessorSymbolNode.class);
      assertThat(((PreprocessorSymbolNode) expression).getSymbol()).isEqualTo(variant.getValue());
    }
  }

  @Test
  void preprocessorAND() {
    var expression = getPreprocessorExpressionTree("Сервер И Клиент");
    assertThat(expression).isInstanceOf(BinaryOperationNode.class);
    var operation = (BinaryOperationNode) expression;
    assertThat(operation.getOperator()).isEqualTo(BslOperator.AND);
    assertThat(operation.getLeft())
      .isInstanceOf(PreprocessorSymbolNode.class)
      .extracting("symbol").isEqualTo(PreprocessorConstraints.SERVER)
    ;
    assertThat(operation.getRight())
      .isInstanceOf(PreprocessorSymbolNode.class)
      .extracting("symbol").isEqualTo(PreprocessorConstraints.CLIENT)
    ;
    expression = getPreprocessorExpressionTree("НЕ Сервер И Клиент");
    assertThat(expression)
      .extracting("left").isInstanceOf(UnaryOperationNode.class)
      .extracting("operand")
      .isInstanceOf(PreprocessorSymbolNode.class)
      .extracting("symbol").isEqualTo(PreprocessorConstraints.SERVER)
    ;
    expression = getPreprocessorExpressionTree("Клиент AND Server AND MobileAppClient");
    operation = (BinaryOperationNode) expression;
    assertThat(operation.getLeft()).isInstanceOf(PreprocessorSymbolNode.class)
      .extracting("symbol").isEqualTo(PreprocessorConstraints.CLIENT);
    assertThat(operation.getRight()).isInstanceOf(BinaryOperationNode.class);
  }

  @Test
  void preprocessorOR() {
    var expression = getPreprocessorExpressionTree("Сервер ИЛИ Клиент");
    assertThat(expression).isInstanceOf(BinaryOperationNode.class);
    var operation = (BinaryOperationNode) expression;
    assertThat(operation.getOperator()).isEqualTo(BslOperator.OR);
    assertThat(operation.getLeft())
      .isInstanceOf(PreprocessorSymbolNode.class)
      .extracting("symbol").isEqualTo(PreprocessorConstraints.SERVER)
    ;
    expression = getPreprocessorExpressionTree("Клиент OR Server OR MobileAppClient");
    operation = (BinaryOperationNode) expression;
    assertThat(operation.getLeft()).isInstanceOf(PreprocessorSymbolNode.class)
      .extracting("symbol").isEqualTo(PreprocessorConstraints.CLIENT);
    assertThat(operation.getRight()).isInstanceOf(BinaryOperationNode.class);
  }

  @Test
  void preprocessorNot() {
    var expression = getPreprocessorExpressionTree("Not Клиент");
    assertThat(expression).isInstanceOf(UnaryOperationNode.class)
      .extracting("operator", "operand.symbol")
      .containsExactly(BslOperator.NOT, PreprocessorConstraints.CLIENT);

    expression = getPreprocessorExpressionTree("Не AtServer");
    assertThat(expression)
      .extracting("operator", "operand.symbol")
      .containsExactly(BslOperator.NOT, PreprocessorConstraints.SERVER);
    expression = getPreprocessorExpressionTree("НЕ (Сервер ИЛИ Клиент)");
    assertThat(expression)
      .isInstanceOf(UnaryOperationNode.class)
      .extracting("operand")
      .isInstanceOf(BinaryOperationNode.class)
      .extracting("left.symbol", "operator", "right.symbol")
      .containsExactly(PreprocessorConstraints.SERVER, BslOperator.OR, PreprocessorConstraints.CLIENT);
  }

  @Test
  void preprocessorComplex() {
    var expression = getPreprocessorExpressionTree("Client AND Not MobileClient OR Server И (ExternalConnection ИЛИ Клиент)");
    var operation = (BinaryOperationNode) expression;
    assertThat(operation.getOperator()).isEqualTo(BslOperator.OR);
    assertThat(operation.getLeft())
      .extracting("left.symbol", "operator", "right.operator", "right.operand.symbol")
      .containsExactly(PreprocessorConstraints.CLIENT, BslOperator.AND, BslOperator.NOT, PreprocessorConstraints.MOBILE_CLIENT)
    ;
    assertThat(operation.getRight())
      .extracting("left.symbol", "operator", "right.left.symbol", "right.operator", "right.right.symbol")
      .containsExactly(PreprocessorConstraints.SERVER, BslOperator.AND, PreprocessorConstraints.EXTERNAL_CONNECTION, BslOperator.OR, PreprocessorConstraints.CLIENT)
    ;
  }

  BslExpression getPreprocessorExpressionTree(String code) {
    var preprocessorPredicate = String.format("#Если %s Тогда\n#КонецЕсли", code);
    var dContext = TestUtils.getDocumentContext(preprocessorPredicate);
    var expression = dContext.getAst().preprocessor(0).preproc_if().preproc_expression();
    return ExpressionParseTreeRewriter.buildExpressionTree(expression);
  }

  BslExpression getExpressionTree(String code) {
    var expression = parse(code);
    return ExpressionParseTreeRewriter.buildExpressionTree(expression);
  }
}