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

/**
 * Базовый класс для обхода дерева выражений.
 * <p>
 * Реализует паттерн Visitor для обхода узлов дерева выражений BSL.
 * Наследники могут переопределять методы visit* для обработки конкретных типов узлов.
 */
public class ExpressionTreeVisitor {

  private void visit(BslExpression node) {
    switch (node.getNodeType()) {
      case CALL -> visitAbstractCall(node.cast());
      case UNARY_OP -> visitUnaryOperation(node.cast());
      case TERNARY_OP -> visitTernaryOperator(node.cast());
      case BINARY_OP -> visitBinaryOperation(node.cast());
      default -> { // для спокойствия сонара
      }
    }
  }

  protected void visitTopLevelExpression(BslExpression node) {
    visit(node);
  }

  protected void visitAbstractCall(AbstractCallNode node) {
    for (var expr : node.arguments()) {
      visit(expr);
    }
  }

  protected void visitUnaryOperation(UnaryOperationNode node) {
    visit(node.getOperand());
  }

  protected void visitBinaryOperation(BinaryOperationNode node) {
    visit(node.getLeft());
    visit(node.getRight());
  }

  protected void visitTernaryOperator(TernaryOperatorNode node) {
    visit(node.getCondition());
    visit(node.getTruePart());
    visit(node.getFalsePart());
  }
}
