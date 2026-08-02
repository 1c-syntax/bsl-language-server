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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionAtPosition;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TerminalSymbolNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Локализация узла dereference'а ({@code ресивер.член}) по терминалу-члену:
 * строит дерево наименьшего охватывающего выражения и находит в нём узел, чья
 * правая часть — заданный терминал.
 * <p>
 * Спуск нужен ровно там, где грамматика <b>не</b> заводит вложенное правило
 * {@code expression}: это операнды бинарной операции и операнд унарной
 * ({@code member: NOT_KEYWORD member}). Всё остальное — аргумент вызова, ветка
 * тернарного оператора, скобки, ключ индекса — само по себе {@code expression},
 * а дерево строится от <b>ближайшего</b> охватывающего, поэтому такой
 * dereference оказывается корнем и до рекурсии дело не доходит.
 * <p>
 * Пропустить нужный вид узла нельзя: обращение к члену останется ненайденным, и
 * потребитель либо не покажет член вовсе (hover, подсветка, completion), либо —
 * если тип ресивера доберётся запасным путём — объявит существующий член
 * неизвестным (диагностика).
 */
@UtilityClass
class DereferenceLocator {

  /**
   * Узел dereference'а, членом которого является {@code terminal}.
   *
   * @param terminal терминал-член ({@code ресивер.член}).
   * @return узел dereference'а; {@code null}, если выражение не строится или
   *     терминал не является членом.
   */
  static @Nullable BinaryOperationNode locate(TerminalNode terminal) {
    return ExpressionAtPosition.findExpressionTree(terminal)
      .map(expression -> locateIn(expression, terminal))
      .orElse(null);
  }

  private static @Nullable BinaryOperationNode locateIn(BslExpression root, TerminalNode terminal) {
    if (root instanceof BinaryOperationNode binary
      && binary.getOperator() == BslOperator.DEREFERENCE
      && isMember(binary.getRight(), terminal)) {
      return binary;
    }
    return firstHit(childrenOf(root), terminal);
  }

  /** Потомки узла, в которых может стоять dereference. */
  private static List<BslExpression> childrenOf(BslExpression node) {
    return switch (node) {
      case BinaryOperationNode binary -> List.of(binary.getLeft(), binary.getRight());
      case UnaryOperationNode unary -> List.of(unary.getOperand());
      default -> List.of();
    };
  }

  private static @Nullable BinaryOperationNode firstHit(List<BslExpression> children, TerminalNode terminal) {
    for (var child : children) {
      var hit = locateIn(child, terminal);
      if (hit != null) {
        return hit;
      }
    }
    return null;
  }

  /** Правая часть dereference'а — это и есть искомый терминал (свойство либо вызов метода). */
  private static boolean isMember(BslExpression right, TerminalNode terminal) {
    if (right instanceof TerminalSymbolNode node && node.getNodeType() == ExpressionNodeType.IDENTIFIER) {
      return node.getRepresentingAst() == terminal;
    }
    return right instanceof MethodCallNode call && call.getName() == terminal;
  }
}
