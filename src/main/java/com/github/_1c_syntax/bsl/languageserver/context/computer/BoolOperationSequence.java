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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.Tree;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Последовательность логических операторов выражения для расчёта сложности.
 * <p>
 * Выражение раскладывается в плоский список токенов {@code И}/{@code ИЛИ} в порядке следования,
 * включая операторы вложенных (в скобках) выражений. Вложенное выражение под {@code НЕ}
 * обрамляется токенами-разделителями с типом {@link #SPLITTER_TYPE}: смена оператора через
 * отрицание считается сменой последовательности.
 */
@UtilityClass
class BoolOperationSequence {

  /** Тип токена-разделителя вокруг выражения под {@code НЕ}. */
  static final int SPLITTER_TYPE = -1;

  /**
   * Разложить выражение в последовательность логических операторов.
   *
   * @param expression выражение
   * @param onExpression вызывается для самого выражения и каждого вложенного, вошедшего в результат
   * @return токены операторов и разделители в порядке следования
   */
  static List<Token> of(BSLParser.ExpressionContext expression, Consumer<BSLParser.ExpressionContext> onExpression) {
    onExpression.accept(expression);

    List<Token> result = new ArrayList<>();
    for (Tree tree : Trees.getChildren(expression)) {
      if (tree instanceof BSLParser.MemberContext member) {
        appendMember(result, member, onExpression);
      } else if (tree instanceof BSLParser.OperationContext operation && operation.boolOperation() != null) {
        result.add(operation.boolOperation().getStart());
      }
    }
    return result;
  }

  private static void appendMember(
    List<Token> result,
    BSLParser.MemberContext member,
    Consumer<BSLParser.ExpressionContext> onExpression
  ) {
    var expression = member.expression();
    if (expression == null) {
      return;
    }

    var nestedTokens = of(expression, onExpression);
    if (nestedTokens.isEmpty()) {
      return;
    }

    var unaryModifier = member.unaryModifier();
    if (unaryModifier != null && unaryModifier.NOT_KEYWORD() != null) {
      var splitter = new CommonToken(SPLITTER_TYPE);
      result.add(splitter);
      result.addAll(nestedTokens);
      result.add(splitter);
    } else {
      result.addAll(nestedTokens);
    }
  }
}
