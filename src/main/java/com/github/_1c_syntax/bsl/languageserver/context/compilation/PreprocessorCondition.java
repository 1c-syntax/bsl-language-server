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
package com.github._1c_syntax.bsl.languageserver.context.compilation;

/** Булева формула без AST; номера символов задаются в пределах одного файла. */
sealed interface PreprocessorCondition {
  enum Value implements PreprocessorCondition { TRUE, FALSE, UNKNOWN }
  record Symbol(int index) implements PreprocessorCondition { }
  record Negation(PreprocessorCondition operand) implements PreprocessorCondition { }
  record Binary(PreprocessorCondition left, PreprocessorCondition right, boolean conjunction) implements PreprocessorCondition { }

  static PreprocessorCondition not(PreprocessorCondition operand) {
    return switch (operand) {
      case Value value -> invert(value);
      case Negation(var nested) -> nested;
      default -> new Negation(operand);
    };
  }

  static PreprocessorCondition and(PreprocessorCondition left, PreprocessorCondition right) {
    return combine(left, right, true);
  }

  static PreprocessorCondition or(PreprocessorCondition left, PreprocessorCondition right) {
    return combine(left, right, false);
  }

  /** Простые тождества сокращают формулы до поиска значений оставшихся символов. */
  private static PreprocessorCondition combine(PreprocessorCondition left, PreprocessorCondition right, boolean conjunction) {
    var absorbing = conjunction ? Value.FALSE : Value.TRUE;
    if (left == absorbing || right == absorbing) {
      return absorbing;
    }
    var neutral = invert(absorbing);
    if (left == neutral) {
      return right;
    }
    if (right == neutral || left.equals(right)) {
      return left;
    }
    if (complements(left, right)) {
      return absorbing;
    }
    return new Binary(left, right, conjunction);
  }

  static Value invert(Value value) {
    return switch (value) {
      case TRUE -> Value.FALSE;
      case FALSE -> Value.TRUE;
      case UNKNOWN -> Value.UNKNOWN;
    };
  }

  private static boolean complements(PreprocessorCondition left, PreprocessorCondition right) {
    return (left instanceof Negation(var leftOperand) && leftOperand.equals(right))
      || (right instanceof Negation(var rightOperand) && rightOperand.equals(left));
  }
}
