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

import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorFlow;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Facts;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowInputs;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Вычисляет построение возвращаемой структуры по достижимой части графа. */
final class ReturnedLocalFlow {
  record Key(BSLParser.CodeBlockContext body, VariableSymbol variable, @Nullable ExecutionContext context) { }

  private record Projection(VariableFlowPass pass, Set<VariableSymbol> variables) { }

  private ReturnedLocalFlow() {
  }

  static @Nullable TypeSet typeAt(
    VariableFlowAnalyzer analyzer, ControlFlowGraphIndex graphs, DocumentContext document, ParserRuleContext use,
    VariableSymbol variable, FlowInputs inputs, BooleanSupplier complete
  ) {
    var body = IndependentReturnedStructure.localVariable(document, variable)
      ? VariableFlowAnalyzer.enclosingBody(use) : null;
    var session = inputs.session();
    if (body == null || session.computing(body)) {
      return null;
    }
    var layout = analyzer.layoutOf(document, body);
    var statement = VariableFlowAnalyzer.enclosingStatement(use, layout);
    if (statement == null) {
      return null;
    }
    inputs.activeBodyRead().accept(body);
    var key = new Key(body, variable, inputs.executionContext());
    var cached = analyzer.cachedFacts(document.getUri(), body, inputs);
    if (cached != null || session.hasIndependentFacts(key)) {
      return typeBefore(cached == null ? session.independentFacts(key) : cached, statement, variable);
    }
    var graph = graphs.graphOf(document, body, CfgBuildOptions.defaults());
    var projection = plan(analyzer, document, layout, key, inputs, graph);
    return project(projection, statement, key, inputs, complete);
  }

  private static @Nullable Projection plan(
    VariableFlowAnalyzer analyzer, DocumentContext document, FlowLayout layout,
    Key key, FlowInputs inputs, ControlFlowGraph graph
  ) {
    var body = key.body();
    var variable = key.variable();
    var variables = analyzer.variablesOf(document, body, layout, inputs);
    var compiled = inputs.compilationContexts().apply(body)
      .map(contexts -> PreprocessorFlow.of(graph, contexts, document.getFileType())).orElse(null);
    var vertices = compiled == null ? graph.vertexSet() : compiled.vertices();
    Predicate<ParserRuleContext> reachable = node -> compiled == null || possiblyReachable(node, layout, vertices);
    if (!IndependentReturnedStructure.supports(document, body, variable, variables, vertices, inputs, reachable)) {
      return null;
    }
    var locals = IndependentReturnedStructure.returnedLocals(document, body, variables, vertices, inputs,
      variable, reachable);
    var pass = new VariableFlowPass(analyzer, document, graph, layout, inputs, compiled, variables.forVariables(locals));
    return new Projection(pass, locals);
  }

  private static @Nullable TypeSet project(@Nullable Projection projection, ParserRuleContext statement,
                                            Key key, FlowInputs inputs, BooleanSupplier complete) {
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
    return typeBefore(facts, statement, key.variable());
  }

  private static boolean possiblyReachable(ParserRuleContext node, FlowLayout layout, Set<CfgVertex> vertices) {
    var statement = VariableFlowAnalyzer.enclosingStatement(node, layout);
    return statement == null || vertices.contains(layout.vertexOf(statement));
  }

  private static @Nullable TypeSet typeBefore(@Nullable Facts facts, ParserRuleContext statement,
                                             VariableSymbol variable) {
    if (facts == null) {
      return null;
    }
    var index = facts.indexOf(variable);
    return index == null ? null : facts.beforeStatement().getOrDefault(statement, FlowEnvironment.EMPTY).get(index);
  }
}
