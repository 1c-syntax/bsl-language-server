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
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 3,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.BADPRACTICE
  }
)
public class DoubleNegativesDiagnostic extends AbstractExpressionTreeDiagnostic {

  @Override
  protected void visitBinaryOperation(BinaryOperationNode node) {

    if (node.getOperator() != BslOperator.EQUAL && node.getOperator() != BslOperator.NOT_EQUAL) {
      super.visitBinaryOperation(node);
      return;
    }

    var parent = node.getParent();

    if (parent == null || !isNegationOperator(parent)) {
      super.visitBinaryOperation(node);
      return;
    }

    if (node.getOperator() == BslOperator.NOT_EQUAL) {
      addDiagnostic(node);
    }

    super.visitBinaryOperation(node);
  }

  @Override
  protected void visitUnaryOperation(UnaryOperationNode node) {
    if (node.getOperator() == BslOperator.NOT &&
      node.getParent() != null &&
      node.getParent().getNodeType() == ExpressionNodeType.UNARY_OP) {

      var unaryParent = node.getParent().<UnaryOperationNode>cast();
      if (unaryParent.getOperator() == BslOperator.NOT) {
        addDiagnostic(node);
      }
    }

    super.visitUnaryOperation(node);
  }

  private static boolean isNegationOperator(BslExpression parent) {
    return parent.getNodeType() == ExpressionNodeType.UNARY_OP
      && parent.<UnaryOperationNode>cast().getOperator() == BslOperator.NOT;
  }

  private void addDiagnostic(BinaryOperationNode node) {
    var startToken = Trees.getTokens(node.getParent().getRepresentingAst())
      .stream()
      .findFirst()
      .orElseThrow();

    var endToken = Trees.getTokens(node.getRight().getRepresentingAst())
      .stream()
      .reduce((one, two) -> two)
      .orElseThrow();

    diagnosticStorage.addDiagnostic(startToken, endToken);
  }

  private void addDiagnostic(UnaryOperationNode node) {
    var startToken = Trees.getTokens(node.getParent().getRepresentingAst())
      .stream()
      .findFirst()
      .orElseThrow();

    var endToken = Trees.getTokens(node.getOperand().getRepresentingAst())
      .stream()
      .reduce((one, two) -> two)
      .orElseThrow();

    diagnosticStorage.addDiagnostic(startToken, endToken);
  }
}
