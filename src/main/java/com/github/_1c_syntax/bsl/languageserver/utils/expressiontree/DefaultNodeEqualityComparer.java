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

import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;

import java.util.List;

/**
 * Стандартный алгоритм сравнения узлов дерева выражений.
 * <p>
 * Сравнивает выражения на структурную идентичность:
 * типы узлов, операторы, значения литералов, имена идентификаторов.
 */
public class DefaultNodeEqualityComparer implements NodeEqualityComparer {

  @Override
  public boolean areEqual(BslExpression first, BslExpression second) {
    if (first == second) {
      return true;
    }

    if (first.getClass() != second.getClass() || first.getNodeType() != second.getNodeType()) {
      return false;
    }

    return switch (first.getNodeType()) {
      case LITERAL -> literalsEqual((TerminalSymbolNode) first, (TerminalSymbolNode) second);
      case IDENTIFIER -> identifiersEqual((TerminalSymbolNode) first, (TerminalSymbolNode) second);
      case BINARY_OP -> binaryOperationsEqual((BinaryOperationNode) first, (BinaryOperationNode) second);
      case UNARY_OP -> unaryOperationsEqual((UnaryOperationNode) first, (UnaryOperationNode) second);
      case TERNARY_OP -> ternaryOperatorsEqual((TernaryOperatorNode) first, (TernaryOperatorNode) second);
      case SKIPPED_CALL_ARG -> true;
      case CALL -> callStatementsEqual((AbstractCallNode) first, (AbstractCallNode) second);
      default -> throw new IllegalStateException();
    };

  }

  protected boolean callStatementsEqual(AbstractCallNode first, AbstractCallNode second) {
    if (first instanceof MethodCallNode methodCallNode) {
      return methodCallsEqual(methodCallNode, (MethodCallNode) second);
    } else {
      return constructorCallsEqual((ConstructorCallNode) first, (ConstructorCallNode) second);
    }
  }

  protected boolean constructorCallsEqual(ConstructorCallNode first, ConstructorCallNode second) {
    return areEqual(first.getTypeName(), second.getTypeName()) && argumentsEqual(first.arguments(), second.arguments());
  }

  protected boolean argumentsEqual(List<BslExpression> argumentsOfFirst, List<BslExpression> argumentsOfSecond) {

    if (argumentsOfFirst.size() != argumentsOfSecond.size()) {
      return false;
    }

    for (var i = 0; i < argumentsOfFirst.size(); i++) {
      if (!areEqual(argumentsOfFirst.get(i), argumentsOfSecond.get(i))) {
        return false;
      }
    }

    return true;
  }

  protected boolean methodCallsEqual(MethodCallNode first, MethodCallNode second) {
    return first.getName().getText().equalsIgnoreCase(second.getName().getText())
      && argumentsEqual(first.arguments(), second.arguments());
  }

  protected boolean ternaryOperatorsEqual(TernaryOperatorNode first, TernaryOperatorNode second) {
    return areEqual(first.getCondition(), second.getCondition())
      && areEqual(first.getTruePart(), second.getTruePart())
      && areEqual(first.getFalsePart(), second.getFalsePart());
  }

  protected boolean unaryOperationsEqual(UnaryOperationNode first, UnaryOperationNode second) {
    if (first.getOperator() != second.getOperator()) {
      return false;
    }

    return areEqual(first.getOperand(), second.getOperand());
  }

  protected boolean binaryOperationsEqual(BinaryOperationNode first, BinaryOperationNode second) {
    if (first.getOperator() != second.getOperator()) {
      return false;
    }

    if (first.getOperator() == BslOperator.DEREFERENCE || first.getOperator() == BslOperator.INDEX_ACCESS) {
      return false;
    }

    return areEqual(first.getLeft(), second.getLeft()) && areEqual(first.getRight(), second.getRight());
  }

  protected boolean identifiersEqual(TerminalSymbolNode first, TerminalSymbolNode second) {
    return DiagnosticHelper.equalNodes(first.getRepresentingAst(), second.getRepresentingAst());
  }

  protected boolean literalsEqual(TerminalSymbolNode first, TerminalSymbolNode second) {
    return DiagnosticHelper.equalNodes(first.getRepresentingAst(), second.getRepresentingAst());
  }
}
