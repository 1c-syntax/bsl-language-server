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
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser.IfStatementContext;
import org.antlr.v4.runtime.tree.Trees;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
class ExpressionTreeBuildingVisitorTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("errorHandlingTestCases")
  @DisplayName("Test error handling in expressions")
  void testErrorHandling(String testName, String code, Class<? extends BslExpression> expectedExpressionClass) {
    var documentContext = TestUtils.getDocumentContext(code);

    var ast = documentContext.getAst();
    var ifStatement = Trees.getDescendants(ast)
      .stream()
      .filter(node -> node instanceof IfStatementContext)
      .map(node -> (IfStatementContext) node)
      .findFirst()
      .orElseThrow();

    var visitor = new ExpressionTreeBuildingVisitor();

    // Find the expression node within the if statement
    var expression = ifStatement.ifBranch().expression();

    // Assert that no exception is thrown when processing problematic expressions
    assertThatNoException().isThrownBy(() -> {
      // Try to build expression tree
      visitor.visitExpression(expression);

      // Result should be an error node
      assertThat(visitor.getExpressionTree()).isInstanceOf(expectedExpressionClass);
    });
  }

  static Stream<Arguments> errorHandlingTestCases() {
    return Stream.of(
      Arguments.of(
        "Empty if statement",
        """
        Процедура Имя()
         Если
           Пока Истина Цикл
           КонецЦикла;
         КонецЕсли;
        КонецПроцедуры
        """,
        ErrorExpressionNode.class
      ),
      createIfStatementTestCase("Empty parentheses", "()"),
      createIfStatementTestCase("Unary operator with error", "() + A"),
      createIfStatementTestCase("Multiply operator", "*"),
      createIfStatementTestCase("Two modulos with identifier", "%%a"),
      createIfStatementTestCase("New expression", "Новый"),
      createIfStatementTestCase("New expression with parenthes", "Новый ("),
      createIfStatementTestCase("New expression with two parantheses", "Новый ()"),
      createIfStatementTestCase("New expression with NOT", "Новый Структура(\"\", Не)", ConstructorCallNode.class),
      createIfStatementTestCase("Incomplete ternary operator - only question mark", "?")
    );
  }

  private static Arguments createIfStatementTestCase(String name, String condition) {
    return createIfStatementTestCase(name, condition, ErrorExpressionNode.class);
  }

  private static Arguments createIfStatementTestCase(String name, String condition, Class<? extends BslExpression> expectedExpressionClass) {
    String code = """
                Процедура Имя()
                  Если %s Тогда

                  КонецЕсли;
                КонецПроцедуры
                """.formatted(condition);
    return Arguments.of(name, code, expectedExpressionClass);
  }

  @Test
  void buildsLiteralFromNumberConstant() {
    // given
    var expression = parseIfCondition("100");
    var visitor = new ExpressionTreeBuildingVisitor();

    // when
    visitor.visitExpression(expression);

    // then
    assertThat(visitor.getExpressionTree()).isInstanceOf(BslExpression.class);
  }

  @Test
  void buildsBinaryOperationFromArithmetic() {
    // given — Если 2 + 3 Тогда
    var expression = parseIfCondition("2 + 3");
    var visitor = new ExpressionTreeBuildingVisitor();

    // when
    visitor.visitExpression(expression);

    // then
    assertThat(visitor.getExpressionTree()).isInstanceOf(BinaryOperationNode.class);
  }

  @Test
  void buildsTernaryOperator() {
    // given — Если ?(А, 1, 2) Тогда
    var expression = parseIfCondition("?(А, 1, 2)");
    var visitor = new ExpressionTreeBuildingVisitor();

    // when
    visitor.visitExpression(expression);

    // then
    assertThat(visitor.getExpressionTree()).isInstanceOf(TernaryOperatorNode.class);
  }

  @Test
  void buildsUnaryOperator() {
    // given — Если -10 Тогда
    var expression = parseIfCondition("-10");
    var visitor = new ExpressionTreeBuildingVisitor();

    // when
    visitor.visitExpression(expression);

    // then
    assertThat(visitor.getExpressionTree()).isInstanceOf(UnaryOperationNode.class);
  }

  @Test
  void buildsConstructorCallForNewExpression() {
    // given — Если Новый Массив() Тогда
    var expression = parseIfCondition("Новый Массив()");
    var visitor = new ExpressionTreeBuildingVisitor();

    // when
    visitor.visitExpression(expression);

    // then
    assertThat(visitor.getExpressionTree()).isInstanceOf(ConstructorCallNode.class);
  }

  @Test
  void buildExpressionTreeFromNullComplexIdentifierReturnsNull() {
    assertThat(ExpressionTreeBuildingVisitor.buildExpressionTree(
      (com.github._1c_syntax.bsl.parser.BSLParser.ComplexIdentifierContext) null)).isNull();
  }

  @Test
  void buildExpressionTreeFromParenthesisExpression() {
    // given — (1 + 2)
    var expression = parseIfCondition("(1 + 2)");
    var visitor = new ExpressionTreeBuildingVisitor();

    // when
    visitor.visitExpression(expression);

    // then
    assertThat(visitor.getExpressionTree()).isNotNull();
  }

  @Test
  void buildExpressionTreeFromNullLValueReturnsNull() {
    assertThat(ExpressionTreeBuildingVisitor.buildExpressionTree(
      (com.github._1c_syntax.bsl.parser.BSLParser.LValueContext) null)).isNull();
  }

  @Test
  void buildExpressionTreeFromNullCallStatementReturnsNull() {
    assertThat(ExpressionTreeBuildingVisitor.buildExpressionTree(
      (com.github._1c_syntax.bsl.parser.BSLParser.CallStatementContext) null)).isNull();
  }

  @Test
  void buildExpressionTreeFromNullExpressionReturnsNull() {
    assertThat(ExpressionTreeBuildingVisitor.buildExpressionTree(
      (com.github._1c_syntax.bsl.parser.BSLParser.ExpressionContext) null)).isNull();
  }

  @Test
  void buildExpressionTreeFromComplexIdentifierReturnsTree() {
    // given — поднимаемся к ComplexIdentifierContext из expression «Объект.Свойство».
    var content = """
                Процедура Имя()
                  А = Объект.Свойство;
                КонецПроцедуры
                """;
    var ast = TestUtils.getDocumentContext(content).getAst();
    var complex = Trees.getDescendants(ast).stream()
      .filter(com.github._1c_syntax.bsl.parser.BSLParser.ComplexIdentifierContext.class::isInstance)
      .map(com.github._1c_syntax.bsl.parser.BSLParser.ComplexIdentifierContext.class::cast)
      .findFirst()
      .orElseThrow();

    // when
    var tree = ExpressionTreeBuildingVisitor.buildExpressionTree(complex);

    // then
    assertThat(tree).isNotNull();
  }

  @Test
  void buildExpressionTreeFromLValueReturnsTree() {
    // given — assignment `Объект.Поле = 1;`, поднимаемся к LValueContext.
    var content = """
                Процедура Имя()
                  Объект.Поле = 1;
                КонецПроцедуры
                """;
    var ast = TestUtils.getDocumentContext(content).getAst();
    var lvalue = Trees.getDescendants(ast).stream()
      .filter(com.github._1c_syntax.bsl.parser.BSLParser.LValueContext.class::isInstance)
      .map(com.github._1c_syntax.bsl.parser.BSLParser.LValueContext.class::cast)
      .findFirst()
      .orElseThrow();

    // when
    var tree = ExpressionTreeBuildingVisitor.buildExpressionTree(lvalue);

    // then
    assertThat(tree).isNotNull();
  }

  @Test
  void buildExpressionTreeFromCallStatementReturnsTree() {
    // given — standalone `Сообщить("...");`.
    var content = """
                Процедура Имя()
                  Сообщить("привет");
                КонецПроцедуры
                """;
    var ast = TestUtils.getDocumentContext(content).getAst();
    var callStmt = Trees.getDescendants(ast).stream()
      .filter(com.github._1c_syntax.bsl.parser.BSLParser.CallStatementContext.class::isInstance)
      .map(com.github._1c_syntax.bsl.parser.BSLParser.CallStatementContext.class::cast)
      .findFirst()
      .orElseThrow();

    // when
    var tree = ExpressionTreeBuildingVisitor.buildExpressionTree(callStmt);

    // then
    assertThat(tree).isNotNull();
  }

  private static com.github._1c_syntax.bsl.parser.BSLParser.ExpressionContext parseIfCondition(String condition) {
    var code = """
                Процедура Имя()
                  Если %s Тогда
                  КонецЕсли;
                КонецПроцедуры
                """.formatted(condition);
    var ast = TestUtils.getDocumentContext(code).getAst();
    var ifStatement = Trees.getDescendants(ast).stream()
      .filter(IfStatementContext.class::isInstance)
      .map(IfStatementContext.class::cast)
      .findFirst()
      .orElseThrow();
    return ifStatement.ifBranch().expression();
  }
}