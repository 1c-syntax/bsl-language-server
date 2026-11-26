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

import com.github._1c_syntax.bsl.languageserver.cfg.CfgEdge;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorFlow;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Facts;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowGraph;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Вычисляет тип локальной переменной в операторе возврата функции или в точке чтения. Сокращает
 * окружение до проверенных независимых результатов и запускает обычный {@link VariableFlowPass}.
 * Окружение проекции переиспользуется в пределах запроса и режима компиляции.
 */
final class ReturnedLocalFlow {
  /**
   * Ключ окружения проекции в текущем запросе.
   *
   * @param body тело функции.
   * @param variable локальный результат.
   * @param context режим компиляции или {@code null} для расчёта без выбранного режима.
   */
  record Key(BSLParser.CodeBlockContext body, VariableSymbol variable, @Nullable ExecutionContext context) { }

  private record Projection(VariableFlowPass pass, Set<VariableSymbol> variables) { }

  private final VariableFlowAnalyzer analyzer;

  ReturnedLocalFlow(VariableFlowAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  /**
   * Читает тип переменной перед оператором возврата. При наличии готовых общих фактов
   * использует их, иначе пробует построить сокращённое окружение.
   *
   * @param document документ текущего дерева.
   * @param use узел использования переменной.
   * @param variable символ возвращаемой переменной.
   * @param inputs входы прохода и сессия вывода типов.
   * @param complete проверка полноты результата перед сохранением в сессии.
   * @return тип в точке возврата или {@code null}, если нужен полный расчёт.
   */
  @Nullable TypeSet typeAt(
    DocumentContext document, ParserRuleContext use,
    VariableSymbol variable, FlowInputs inputs, BooleanSupplier complete
  ) {
    var body = IndependentReturnedStructure.localVariable(variable)
      ? FlowLayout.enclosingBody(use) : null;
    if (body == null || inputs.session().computing(body)) {
      return null;
    }
    var layout = analyzer.layoutOf(document, body);
    var statement = layout.enclosingStatement(use);
    if (statement == null) {
      return null;
    }
    inputs.activeBodyRead().accept(body);
    var key = new Key(body, variable, inputs.executionContext());
    return typeBefore(factsForReturn(document, layout, key, inputs, complete), statement, variable);
  }

  private @Nullable Facts factsForReturn(DocumentContext document, FlowLayout layout, Key key,
                                         FlowInputs inputs, BooleanSupplier complete) {
    var session = inputs.session();
    var cached = analyzer.cachedFacts(document.getUri(), key.body(), inputs);
    if (cached != null || session.hasIndependentFacts(key)) {
      session.noteCellInput();
      return cached == null ? session.independentFacts(key) : cached;
    }
    var graph = analyzer.flowGraph(document, key.body(), inputs);
    var projection = plan(document, layout, key, inputs, graph, true);
    return project(projection, key, inputs, complete);
  }

  @Nullable Facts readFacts(DocumentContext document, FlowLayout layout, Key key, FlowInputs inputs) {
    var graph = analyzer.flowGraph(document, key.body(), inputs);
    var projection = plan(document, layout, key, inputs, graph, false);
    return projection == null ? null : inputs.session().computeBodyFacts(key.body(), projection.pass());
  }

  /** Посчитать вход параметра в заголовок обхода по вершинам, от которых он зависит. */
  @Nullable Facts readParameterBeforeLoop(DocumentContext document, FlowLayout layout,
    Key key, CfgVertex loop, FlowInputs inputs) {
    var body = key.body();
    var flow = analyzer.flowGraph(document, body, inputs);
    var graph = flow.graph();
    var compiled = flow.compiled();
    if (compiled != null && !compiled.vertices().contains(loop)) {
      return null;
    }
    var beforeLoop = predecessorsOf(graph, loop, compiled);
    var variables = analyzer.variablesOf(document, body, layout, inputs);
    if (!beforeLoop.vertices().contains(graph.getEntryPoint())
      || !supportsParameter(document, key, inputs, variables, beforeLoop.vertices(),
        node -> possiblyReachable(node, layout, beforeLoop.vertices()), true)) {
      return null;
    }
    var pass = new VariableFlowPass(analyzer, document, graph, layout, inputs, beforeLoop,
      variables.forVariables(Set.of(key.variable())));
    return inputs.session().computeBodyFacts(body, pass);
  }

  /** Все входящие переходы, по которым значение может попасть в выбранную вершину. */
  private static PreprocessorFlow predecessorsOf(ControlFlowGraph graph, CfgVertex vertex,
                                                 @Nullable PreprocessorFlow compiled) {
    Set<CfgVertex> vertices = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<CfgEdge> edges = Collections.newSetFromMap(new IdentityHashMap<>());
    var pending = new ArrayDeque<CfgVertex>();
    vertices.add(vertex);
    pending.add(vertex);
    while (!pending.isEmpty()) {
      for (var edge : graph.incomingEdgesOf(pending.removeFirst())) {
        if (compiled != null && !compiled.edges().contains(edge)) {
          continue;
        }
        edges.add(edge);
        var source = graph.getEdgeSource(edge);
        if (vertices.add(source)) {
          pending.addLast(source);
        }
      }
    }
    return new PreprocessorFlow(vertices, edges);
  }

  private @Nullable Projection plan(
    DocumentContext document, FlowLayout layout,
    Key key, FlowInputs inputs, FlowGraph flow, boolean returning
  ) {
    var body = key.body();
    var variables = analyzer.variablesOf(document, body, layout, inputs);
    var graph = flow.graph();
    var compiled = flow.compiled();
    var vertices = compiled == null ? graph.vertexSet() : compiled.vertices();
    var reachable = new IndependentReturnedStructure.Reachability(vertices,
      node -> compiled == null || possiblyReachable(node, layout, vertices));
    if (!supports(document, key, inputs, variables, reachable, returning)) {
      return null;
    }
    var variable = key.variable();
    var locals = returning
      ? IndependentReturnedStructure.returnedLocals(document, body, variables, inputs, variable, reachable)
      : Set.of(variable);
    var pass = new VariableFlowPass(analyzer, document, graph, layout, inputs, compiled, variables.forVariables(locals));
    return new Projection(pass, locals);
  }

  private static boolean supports(DocumentContext document, Key key, FlowInputs inputs, FlowVariables variables,
                                  IndependentReturnedStructure.Reachability reachable, boolean returning) {
    var variable = key.variable();
    if (!returning && variable.getKind() == VariableKind.PARAMETER) {
      return supportsParameter(document, key, inputs, variables, reachable.vertices(), reachable.statements(), false);
    }
    return IndependentReturnedStructure.supports(document, key.body(), variable, variables, inputs, reachable, returning);
  }

  private static boolean supportsParameter(DocumentContext document, Key key, FlowInputs inputs,
    FlowVariables variables, Set<CfgVertex> vertices, Predicate<ParserRuleContext> reachable, boolean directEscapeOnly) {
    var variable = key.variable();
    return variables.indexes().containsKey(variable)
      && variables.changesByStatement().entrySet().stream().filter(entry -> reachable.test(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream()).noneMatch(change -> change.variable() == variable)
      && IndependentReturnedStructure.closedConditions(variable, vertices, inputs)
      && !IndependentReturnedStructure.escapes(document, key.body(), variable, reachable, directEscapeOnly);
  }

  private static @Nullable Facts project(@Nullable Projection projection, Key key,
                                         FlowInputs inputs, BooleanSupplier complete) {
    var session = inputs.session();
    if (projection == null) {
      if (inputs.cacheable()) {
        session.rememberIndependentFacts(key, null, inputs, complete);
      }
      return null;
    }
    var facts = session.computeBodyFacts(key.body(), projection.pass());
    for (var local : projection.variables()) {
      session.rememberIndependentFacts(new Key(key.body(), local, key.context()), facts, inputs, complete);
    }
    return facts;
  }

  private static boolean possiblyReachable(ParserRuleContext node, FlowLayout layout, Set<CfgVertex> vertices) {
    var statement = layout.enclosingStatement(node);
    return statement == null || vertices.contains(layout.vertexOf(statement));
  }

  private static @Nullable TypeSet typeBefore(@Nullable Facts facts, ParserRuleContext statement,
                                             VariableSymbol variable) {
    return facts == null ? null : facts.typeBefore(statement, variable);
  }
}
