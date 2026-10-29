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
package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorContexts;
import com.github._1c_syntax.bsl.parser.BSLParser;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Достижимые вершины и рёбра CFG для множества возможных контекстов компиляции.
 * <p>
 * Фабрика {@link #of} обходит граф от входа, исключая ложные ветки препроцессора.
 * При неизвестном условии сохраняются обе ветки. Остальные условия не сужаются
 * дополнительно; рёбра {@link CfgEdgeType#ADJACENT_CODE} не участвуют в достижимости.
 * <p>
 * Исходный граф не изменяется. Вершины и рёбра не копируются и сравниваются по идентичности.
 * Фабрика возвращает неизменяемые множества; прямой конструктор record сохраняет переданные
 * множества без защитного копирования.
 *
 * @param vertices вершины, достижимые из входа хотя бы в одном из выбранных контекстов.
 * @param edges рёбра достижимых переходов.
 */
public record PreprocessorFlow(Set<CfgVertex> vertices, Set<CfgEdge> edges) {

  /**
   * Построить представление достижимой части графа с учётом препроцессора.
   * <p>
   * Условия вычисляются через {@link PreprocessorContexts}. Исключаются и рёбра веток,
   * и недостижимые по оставшимся рёбрам вершины: их операторы не должны влиять на типы.
   *
   * <p>
   * Контексты распространяются по рёбрам от входа: вложенная ветка не может восстановить
   * режим, исключённый внешним условием. Это сохраняет противоречия и при объединении режимов.
   *
   * @param graph исходный граф с вершинами условий препроцессора.
   * @param contexts возможные контексты тела; пустое множество исключает всё тело.
   * @return новое представление с неизменяемыми множествами объектов исходного графа.
   */
  @SuppressWarnings("java:S6411") // IdentityHashMap сравнивает вершины по ссылке; Comparable в ней не участвует.
  public static PreprocessorFlow of(ControlFlowGraph graph, Set<ExecutionContext> contexts) {
    if (contexts.isEmpty()) {
      return new PreprocessorFlow(Set.of(), Set.of());
    }
    var reachable = new IdentityHashMap<CfgVertex, Set<ExecutionContext>>();
    Set<CfgEdge> edges = Collections.newSetFromMap(new IdentityHashMap<>());
    var pending = new ArrayDeque<CfgVertex>();
    reachable.put(graph.getEntryPoint(), ExecutionContext.copyOf(contexts));
    pending.add(graph.getEntryPoint());
    while (!pending.isEmpty()) {
      var vertex = pending.removeFirst();
      for (var edge : graph.outgoingEdgesOf(vertex)) {
        var allowed = allowedContexts(vertex, edge.getType(), reachable.get(vertex));
        if (allowed.isEmpty()) {
          continue;
        }
        edges.add(edge);
        var target = graph.getEdgeTarget(edge);
        var atTarget = reachable.computeIfAbsent(target, ignored -> EnumSet.noneOf(ExecutionContext.class));
        if (atTarget.addAll(allowed)) {
          pending.addLast(target);
        }
      }
    }
    Set<CfgVertex> vertices = Collections.newSetFromMap(new IdentityHashMap<>());
    vertices.addAll(reachable.keySet());
    return new PreprocessorFlow(Collections.unmodifiableSet(vertices), Collections.unmodifiableSet(edges));
  }

  private static Set<ExecutionContext> allowedContexts(CfgVertex vertex, CfgEdgeType edge,
                                                       Set<ExecutionContext> contexts) {
    if (!(vertex instanceof PreprocessorConditionVertex)
        || (edge != CfgEdgeType.TRUE_BRANCH && edge != CfgEdgeType.FALSE_BRANCH)) {
      return edge == CfgEdgeType.ADJACENT_CODE ? Set.of() : contexts;
    }
    var expression = vertex.getAst().orElseThrow()
      .getRuleContext(BSLParser.Preproc_expressionContext.class, 0);
    var active = PreprocessorContexts.evaluate(expression);
    if (active == null) {
      return contexts;
    }
    var allowed = ExecutionContext.copyOf(contexts);
    if (edge == CfgEdgeType.TRUE_BRANCH) {
      allowed.retainAll(active);
    } else {
      allowed.removeAll(active);
    }
    return allowed;
  }
}
