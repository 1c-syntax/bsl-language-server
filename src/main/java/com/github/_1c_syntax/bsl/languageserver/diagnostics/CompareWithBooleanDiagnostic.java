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
import com.github._1c_syntax.bsl.parser.BSLParser;

/**
 * Диагностика, выявляющая избыточное сравнение выражений с булевой константой
 * {@code Истина}/{@code Ложь} через операторы {@code =} и {@code <>}
 * (например {@code Если Значение = Истина Тогда}).
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.CLUMSY,
    DiagnosticTag.SUSPICIOUS
  }
)
public class CompareWithBooleanDiagnostic extends AbstractExpressionTreeDiagnostic {

  /**
   * Проверяет бинарную операцию на избыточное сравнение с булевой константой.
   * Диагностика срабатывает, если операция является сравнением ({@code =} или {@code <>})
   * и хотя бы один из её операндов — булева константа ({@code Истина}/{@code Ложь}),
   * например {@code Значение = Истина} или {@code Истина <> Значение}.
   *
   * @param node узел бинарной операции дерева выражений
   */
  @Override
  protected void visitBinaryOperation(BinaryOperationNode node) {

    if (node.getOperator() != BslOperator.EQUAL && node.getOperator() != BslOperator.NOT_EQUAL) {
      super.visitBinaryOperation(node);
      return;
    }

    if (isBooleanLiteral(node.getLeft()) || isBooleanLiteral(node.getRight())) {
      addDiagnostic(node);
    }

    super.visitBinaryOperation(node);
  }

  /**
   * Определяет, является ли узел выражения булевой константой.
   * Распознаются как русские ({@code Истина}/{@code Ложь}), так и английские
   * ({@code True}/{@code False}) литералы.
   *
   * @param expression проверяемый узел дерева выражений
   * @return {@code true}, если узел — литерал {@code Истина}/{@code Ложь}, иначе {@code false}
   */
  private static boolean isBooleanLiteral(BslExpression expression) {
    if (expression.getNodeType() != ExpressionNodeType.LITERAL) {
      return false;
    }

    var ast = expression.getRepresentingAst();
    if (!(ast instanceof BSLParser.ConstValueContext constValue)) {
      return false;
    }

    return constValue.getToken(BSLParser.TRUE, 0) != null
      || constValue.getToken(BSLParser.FALSE, 0) != null;
  }

  /**
   * Регистрирует замечание на всё выражение сравнения — от первого токена левого операнда
   * до последнего токена правого операнда.
   *
   * @param node узел бинарной операции, на которую добавляется замечание
   */
  private void addDiagnostic(BinaryOperationNode node) {
    var startToken = Trees.getTokens(node.getLeft().getRepresentingAst())
      .stream()
      .findFirst()
      .orElseThrow();

    var endToken = Trees.getTokens(node.getRight().getRepresentingAst())
      .stream()
      .reduce((first, second) -> second)
      .orElseThrow();

    diagnosticStorage.addDiagnostic(startToken, endToken);
  }
}
