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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgEdgeType;
import com.github._1c_syntax.bsl.languageserver.cfg.ConditionalVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.WhileLoopVertex;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Environment;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.GuardNarrowing;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Сужение окружения на рёбрах условий Если и Пока. */
final class FlowConditionNarrowing {
  private FlowConditionNarrowing() {
  }

  /**
   * Окружение, прошедшее по ребру ветки условия. Условие цикла {@code Пока} сужает так
   * же, как условие {@code Если}: на входе в тело оно верно, за циклом — ложно.
   */
  @SuppressWarnings("java:S6411") // Номера привязаны к идентичности символов одного тела.
  static Environment narrow(CfgVertex predecessor, CfgEdgeType edgeType, Environment outgoing,
                            GuardNarrowing narrowing, List<VariableSymbol> variables,
                            Map<VariableSymbol, Integer> indexes) {
    if (edgeType != CfgEdgeType.TRUE_BRANCH && edgeType != CfgEdgeType.FALSE_BRANCH) {
      return outgoing;
    }
    var condition = conditionOf(predecessor);
    if (condition == null) {
      return outgoing;
    }
    // Сужаем только переменные, о которых условие правда что-то утверждает: перебор всех
    // переменных тела давал бы вызов колбэка на каждую, а условие обычно про одну.
    var mentioned = narrowing.variablesOf(condition);
    if (mentioned.isEmpty()) {
      return outgoing;
    }
    var whenTrue = edgeType == CfgEdgeType.TRUE_BRANCH;
    var narrowed = outgoing;
    for (var symbol : mentioned) {
      var index = indexes.get(symbol);
      if (index == null) {
        continue;
      }
      var variable = variables.get(index);
      narrowed = narrowed.with(
        index,
        narrowing.narrow(variable, condition, whenTrue, narrowed.get(index)),
        variables.size());
    }
    return narrowed;
  }

  /** Условие вершины-ветвления; {@code null}, если вершина условия не несёт. */
  private static BSLParser.@Nullable ExpressionContext conditionOf(CfgVertex vertex) {
    if (vertex instanceof ConditionalVertex conditional) {
      return conditional.getExpression();
    }
    return vertex instanceof WhileLoopVertex loop ? loop.getExpression() : null;
  }
}
