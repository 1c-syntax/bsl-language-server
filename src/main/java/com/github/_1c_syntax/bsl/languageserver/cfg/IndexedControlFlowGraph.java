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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.utils.Lazy;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

/** Общий граф и его неизменяемые проекции препроцессора на время жизни дерева разбора. */
public final class IndexedControlFlowGraph {
  private static final PreprocessorFlow EMPTY_FLOW = new PreprocessorFlow(Set.of(), Set.of());

  private final ControlFlowGraph graph;
  private final FileType fileType;
  private final Lazy<Function<Set<ExecutionContext>, PreprocessorFlow>> projections;

  IndexedControlFlowGraph(ControlFlowGraph graph, FileType fileType) {
    this.graph = graph;
    this.fileType = fileType;
    projections = new Lazy<>(this::createProjection);
  }

  /** @return исходный граф, общий для потребителей и проекций. */
  public ControlFlowGraph graph() {
    return graph;
  }

  /**
   * @param contexts возможные режимы тела. Пустой набор исключает всё тело.
   * @return доступные вершины и рёбра исходного графа.
   */
  public PreprocessorFlow compiledFlow(Set<ExecutionContext> contexts) {
    return contexts.isEmpty() ? EMPTY_FLOW : projections.getOrCompute().apply(contexts);
  }

  private Function<Set<ExecutionContext>, PreprocessorFlow> createProjection() {
    if (graph.vertexSet().stream().noneMatch(PreprocessorConditionVertex.class::isInstance)) {
      var common = PreprocessorFlow.of(graph, EnumSet.of(ExecutionContext.SERVER), fileType);
      return (Set<ExecutionContext> contexts) -> common;
    }

    Cache<Set<ExecutionContext>, PreprocessorFlow> byContexts = Caffeine.newBuilder().build();
    return (Set<ExecutionContext> contexts) -> {
      var existing = byContexts.getIfPresent(contexts);
      return existing != null ? existing
        : byContexts.get(Set.copyOf(contexts), active -> PreprocessorFlow.of(graph, active, fileType));
    };
  }
}
