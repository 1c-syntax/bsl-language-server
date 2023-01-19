/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
 * Стратегия сравнения выражений с учетом транзитивности операторов
 * А = Б будет эквивалентно Б = А
 * если включить режим транзитивности логических операций, то эквивалентны будут
 * "А и Б" и "Б и А" (см. метод {@link com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TransitiveOperationsIgnoringComparer#logicalOperationsAsTransitive(boolean)}})
 */
public class TransitiveOperationsIgnoringComparer extends DefaultNodeEqualityComparer {

  private boolean logicalsAreTransitive = false;

  @Override
  protected boolean binaryOperationsEqual(BinaryOperationNode first, BinaryOperationNode second) {

    if (first.getOperator() != second.getOperator()) {
      return false;
    }

    var operator = first.getOperator();
    if (isTransitiveOperation(operator)) {
      return super.binaryOperationsEqual(first, second) ||
        (areEqual(first.getLeft(), second.getRight()) && areEqual(first.getRight(), second.getLeft()));
    }

    return super.binaryOperationsEqual(first, second);

  }

  /**
   * @param transitivityFlag Включает режим транзитивности логических операций
   *                         если true, то операторы И/ИЛИ считаются транзитивными
   *                         (не учитывается сокращенное выполнение логических операций)
   */
  public void logicalOperationsAsTransitive(boolean transitivityFlag) {
    logicalsAreTransitive = transitivityFlag;
  }

  private boolean isTransitiveOperation(BslOperator operator) {
    if (operator == BslOperator.DEREFERENCE || operator == BslOperator.INDEX_ACCESS) {
      return false;
    }

    return operator == BslOperator.ADD ||
      operator == BslOperator.EQUAL ||
      operator == BslOperator.MULTIPLY ||
      isTransitiveLogicalOp(operator);
  }

  private boolean isTransitiveLogicalOp(BslOperator operator) {
    return logicalsAreTransitive && (operator == BslOperator.AND || operator == BslOperator.OR);
  }
}
