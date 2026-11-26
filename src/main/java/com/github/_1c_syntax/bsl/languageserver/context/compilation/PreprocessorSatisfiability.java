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

import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.Value;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.Negation;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.Binary;

import static com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.invert;

/**
 * Поиск удовлетворяющего набора значений с отсечением уже ложных подформул.
 * Лимит общий для всех попыток одного запроса; незавершённый поиск сохраняет ветку.
 */
final class PreprocessorSatisfiability {
  /** Общий предел вычислений для одного запроса достижимости. */
  private static final int MAX_EVALUATION_STEPS = 16_384;

  private final boolean[] assigned;
  private final boolean[] values;
  private int steps = MAX_EVALUATION_STEPS;
  private int undecided;

  PreprocessorSatisfiability(int symbols) {
    assigned = new boolean[symbols];
    values = new boolean[symbols];
  }

  boolean canBeTrue(PreprocessorCondition condition) {
    undecided = -1;
    var result = evaluate(condition);
    if (steps < 0 || result == Value.TRUE) {
      return true;
    }
    if (result == Value.FALSE) {
      return false;
    }
    if (undecided < 0) {
      return true; // Ошибка разбора, а не переменная, которую можно уточнить.
    }
    var symbol = undecided;
    assigned[symbol] = true;
    values[symbol] = false;
    var possible = canBeTrue(condition);
    if (!possible) {
      values[symbol] = true;
      possible = canBeTrue(condition);
    }
    assigned[symbol] = false;
    return possible;
  }

  private Value evaluate(PreprocessorCondition condition) {
    steps--;
    if (steps < 0) {
      return Value.UNKNOWN;
    }
    return switch (condition) {
      case Value value -> value;
      case Symbol(var index) -> symbolValue(index);
      case Negation(var operand) -> invert(evaluate(operand));
      case Binary(var left, var right, var conjunction) -> evaluateBinary(left, right, conjunction);
    };
  }

  private Value symbolValue(int index) {
    if (assigned[index]) {
      return values[index] ? Value.TRUE : Value.FALSE;
    }
    if (undecided < 0) {
      undecided = index;
    }
    return Value.UNKNOWN;
  }

  private Value evaluateBinary(PreprocessorCondition leftCondition, PreprocessorCondition rightCondition,
                               boolean conjunction) {
    var absorbing = conjunction ? Value.FALSE : Value.TRUE;
    var left = evaluate(leftCondition);
    if (left == absorbing) {
      return absorbing;
    }
    var right = evaluate(rightCondition);
    if (right == absorbing) {
      return absorbing;
    }
    var neutral = invert(absorbing);
    if (left == neutral) {
      return right;
    }
    return right == neutral ? left : Value.UNKNOWN;
  }
}
