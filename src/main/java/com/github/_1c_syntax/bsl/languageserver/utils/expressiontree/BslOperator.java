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

public enum BslOperator {
  // binary
  ADD,
  SUBTRACT,
  MULTIPLY,
  DIVIDE,
  MODULO,
  DEREFERENCE,
  INDEX_ACCESS,
  EQUAL,
  LESS,
  LESS_OR_EQUAL,
  GREATER,
  GREATER_OR_EQUAL,
  NOT_EQUAL,
  AND,
  OR,
  // unary
  UNARY_MINUS,
  UNARY_PLUS,
  NOT,
  //ternary,
  CONDITIONAL;

  public static int getPriority(BslOperator operator) {
    switch (operator){
      case ADD:
      case SUBTRACT:
        return 1;
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
        return 2;
      case EQUAL:
      case NOT_EQUAL:
      case LESS:
      case LESS_OR_EQUAL:
      case GREATER:
      case GREATER_OR_EQUAL:
        return 3;
      case AND:
        return 4;
      case OR:
        return 5;
      case NOT:
        return 6;
      case UNARY_MINUS:
      case UNARY_PLUS:
        return 7;
      case INDEX_ACCESS:
      case DEREFERENCE:
        return 8;
      case CONDITIONAL:
        return 9;
    }

    throw new IllegalArgumentException();
  }

}
