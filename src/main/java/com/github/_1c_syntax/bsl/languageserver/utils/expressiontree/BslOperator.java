/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

/**
 * Операторы языка BSL.
 * <p>
 * Перечисление всех операторов с указанием их приоритета выполнения.
 * Меньшее значение приоритета означает более раннее выполнение.
 */
@Getter
@RequiredArgsConstructor
public enum BslOperator {

  /**
   * Логическое ИЛИ (приоритет 1).
   */
  OR(1),
  /**
   * Логическое И (приоритет 2).
   */
  AND(2),
  /**
   * Логическое НЕ (приоритет 3).
   */
  NOT(3),

  /**
   * Равно (приоритет 4).
   */
  EQUAL(4),
  /**
   * Меньше (приоритет 4).
   */
  LESS(4),
  /**
   * Меньше или равно (приоритет 4).
   */
  LESS_OR_EQUAL(4),
  /**
   * Больше (приоритет 4).
   */
  GREATER(4),
  /**
   * Больше или равно (приоритет 4).
   */
  GREATER_OR_EQUAL(4),
  /**
   * Не равно (приоритет 4).
   */
  NOT_EQUAL(4),

  /**
   * Сложение (приоритет 5).
   */
  ADD(5),
  /**
   * Вычитание (приоритет 5).
   */
  SUBTRACT(5),

  /**
   * Умножение (приоритет 6).
   */
  MULTIPLY(6),
  /**
   * Деление (приоритет 6).
   */
  DIVIDE(6),
  /**
   * Остаток от деления (приоритет 6).
   */
  MODULO(6),

  /**
   * Унарный минус (приоритет 7).
   */
  UNARY_MINUS(7),
  UNARY_PLUS(7),

  DEREFERENCE(8),
  INDEX_ACCESS(8);

  private final int priority;
}
