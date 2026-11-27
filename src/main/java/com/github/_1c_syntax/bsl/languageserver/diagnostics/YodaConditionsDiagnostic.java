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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TernaryOperatorNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import org.antlr.v4.runtime.Token;

import java.util.EnumSet;
import java.util.Set;

/**
 * Диагностика, выявляющая сравнения, в которых литерал стоит слева от оператора
 * ({@code Если 0 = Сумма}), вместо привычного порядка «выражение — оператор — литерал».
 * <p>
 * Литералом считается любая константа языка (число, строка, дата, булево значение,
 * {@code Неопределено}), а также унарный плюс или минус над константой.
 * Сравнение двух литералов нарушением не считается: переставлять в нём нечего.
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.BRAINOVERLOAD
  }
)
public class YodaConditionsDiagnostic extends AbstractExpressionTreeDiagnostic {

  private static final Set<BslOperator> COMPARISON_OPERATORS = EnumSet.of(
    BslOperator.EQUAL,
    BslOperator.NOT_EQUAL,
    BslOperator.LESS,
    BslOperator.LESS_OR_EQUAL,
    BslOperator.GREATER,
    BslOperator.GREATER_OR_EQUAL
  );

  /**
   * Проверяет бинарную операцию на «йода-синтаксис»: литерал слева, не литерал справа.
   * Замечание ставится на всё выражение сравнения, после чего обход продолжается,
   * чтобы поймать сравнения, вложенные в логические связки и аргументы вызовов.
   *
   * @param node узел бинарной операции дерева выражений
   */
  @Override
  protected void visitBinaryOperation(BinaryOperationNode node) {
    if (COMPARISON_OPERATORS.contains(node.getOperator())
      && isLiteral(node.getLeft())
      && !isLiteral(node.getRight())) {
      addDiagnostic(node);
    }

    super.visitBinaryOperation(node);
  }

  /**
   * Определяет, является ли выражение литералом: константой либо унарным
   * плюсом/минусом над константой.
   *
   * @param expression проверяемый узел дерева выражений
   * @return {@code true}, если выражение можно считать литералом
   */
  private static boolean isLiteral(BslExpression expression) {
    if (expression.getNodeType() == ExpressionNodeType.LITERAL) {
      return true;
    }

    if (expression instanceof UnaryOperationNode unaryOperation) {
      var operator = unaryOperation.getOperator();
      return (operator == BslOperator.UNARY_MINUS || operator == BslOperator.UNARY_PLUS)
        && isLiteral(unaryOperation.getOperand());
    }

    return false;
  }

  /**
   * Регистрирует замечание на всё выражение сравнения — от первого токена левого
   * операнда до последнего токена правого операнда.
   *
   * @param node узел бинарной операции, на которую добавляется замечание
   */
  private void addDiagnostic(BinaryOperationNode node) {
    var startToken = Trees.getTokens(node.getLeft().getRepresentingAst())
      .stream()
      .findFirst()
      .orElseThrow();

    var endToken = getLastToken(node.getRight());

    diagnosticStorage.addDiagnostic(startToken, endToken);
  }

  /**
   * Возвращает последний токен выражения. У операций и тернарного оператора
   * {@code representingAst} указывает на сам оператор или на всё выражение,
   * поэтому для них спускаемся к последнему смысловому операнду.
   *
   * @param expression узел дерева выражений
   * @return последний токен полного выражения
   */
  private static Token getLastToken(BslExpression expression) {
    if (expression instanceof BinaryOperationNode binaryOperation) {
      return getLastToken(binaryOperation.getRight());
    }
    if (expression instanceof UnaryOperationNode unaryOperation) {
      return getLastToken(unaryOperation.getOperand());
    }
    if (expression instanceof TernaryOperatorNode ternaryOperator) {
      return getLastToken(ternaryOperator.getFalsePart());
    }
    return Trees.getTokens(expression.getRepresentingAst()).getLast();
  }
}
