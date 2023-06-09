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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BslOperator {

  OR(1),
  AND(2),
  NOT(3),

  EQUAL(4),
  LESS(4),
  LESS_OR_EQUAL(4),
  GREATER(4),
  GREATER_OR_EQUAL(4),
  NOT_EQUAL(4),

  ADD(5),
  SUBTRACT(5),

  MULTIPLY(6),
  DIVIDE(6),
  MODULO(6),

  // unary
  UNARY_MINUS(7),
  UNARY_PLUS(7),

  DEREFERENCE(8),
  INDEX_ACCESS(8);

  private final int priority;
}
