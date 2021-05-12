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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BslOperator {

  // binary
  ADD(1),
  SUBTRACT(1),

  MULTIPLY(2),
  DIVIDE(2),
  MODULO(2),

  EQUAL(3),
  LESS(3),
  LESS_OR_EQUAL(3),
  GREATER(3),
  GREATER_OR_EQUAL(3),
  NOT_EQUAL(3),

  AND(4),
  OR(5),

  NOT(6),

  // unary
  UNARY_MINUS(7),
  UNARY_PLUS(7),

  DEREFERENCE(8),
  INDEX_ACCESS(8),

  //ternary,
  CONDITIONAL(9);

  private final int priority;

}
