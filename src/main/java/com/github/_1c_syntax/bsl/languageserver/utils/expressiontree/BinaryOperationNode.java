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

package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

public class BinaryOperationNode extends BslOperationNode{
  private BslExpression leftOperand;
  private BslExpression rightOperand;

  private BinaryOperationNode(BslOperator operator) {
    super(ExpressionNodeType.BINARY_OP, operator);
  }

  private void setLeft(BslExpression left){
    leftOperand = left;
  }

  private void setRight(BslExpression right){
    rightOperand = right;
  }

  public BslExpression getLeft() {
    return leftOperand;
  }

  public BslExpression getRight() {
    return rightOperand;
  }

  public static BinaryOperationNode create(BslOperator operator, BslExpression left, BslExpression right){
    var node = new BinaryOperationNode(operator);
    node.setLeft(left);
    node.setRight(right);
    return node;
  }
}
