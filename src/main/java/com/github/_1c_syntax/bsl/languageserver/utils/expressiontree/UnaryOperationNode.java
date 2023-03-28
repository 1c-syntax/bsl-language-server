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

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Класс, представляющий унарное выражение
 */
@Value
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class UnaryOperationNode extends BslOperationNode {
  BslExpression operand;

  private UnaryOperationNode(BslOperator operator, BslExpression operand, ParseTree operationContext) {
    super(ExpressionNodeType.UNARY_OP, operator, operationContext);
    this.operand = operand;
  }

  /**
   * Конструирует унарную операцию
   *
   * @param operator         оператор
   * @param expression       аргумент операции
   * @param operationContext строковое представление оператора,
   *                         как он указан в коде с учетом регистра и языка.
   *                         Используется в диагностических сообщениях.
   * @return созданная ветка унарной операции
   */
  public static UnaryOperationNode create(BslOperator operator, BslExpression expression, ParseTree operationContext) {
    return new UnaryOperationNode(operator, expression, operationContext);
  }

}
