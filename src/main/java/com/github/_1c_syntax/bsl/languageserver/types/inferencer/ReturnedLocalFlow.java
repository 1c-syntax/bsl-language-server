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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Facts;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowGraph;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowInputs;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Вычисляет тип локальной переменной в операторе возврата функции. Сокращает
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

  private record Projection(FlowGraph flow, FlowVariables inputVariables, Set<VariableSymbol> locals) { }

  private ReturnedLocalFlow() {
  }

  /**
   * Читает тип переменной перед оператором возврата. При наличии готовых общих фактов
   * использует их, иначе пробует построить сокращённое окружение.
   *
   * @param analyzer индекс графа и фактов в рабочей области.
   * @param document документ текущего дерева.
   * @param use узел использования переменной.
   * @param variable символ возвращаемой переменной.
   * @param inputs входы прохода и сессия вывода типов.
   * @param complete проверка полноты результата перед сохранением в сессии.
   * @return тип в точке возврата или {@code null}, если нужен полный расчёт.
   */
  static @Nullable TypeSet typeAt(
    VariableFlowAnalyzer analyzer, DocumentContext document, ParserRuleContext use,
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
    return typeBefore(factsForReturn(analyzer, document, layout, key, inputs, complete), statement, variable);
  }

  private static @Nullable Facts factsForReturn(
    VariableFlowAnalyzer analyzer, DocumentContext document, FlowLayout layout, Key key,
    FlowInputs inputs, BooleanSupplier complete
  ) {
    var session = inputs.session();
    var cached = analyzer.cachedFacts(document.getUri(), key.body(), inputs);
    if (cached != null || session.hasIndependentFacts(key)) {
      session.noteCellInput();
      return cached == null ? session.independentFacts(key) : cached;
    }
    var projection = plan(analyzer, document, layout, key, inputs);
    if (projection == null) {
      if (inputs.cacheable()) {
        session.rememberIndependentFacts(key, null, inputs, complete);
      }
      return null;
    }

    var facts = analyzer.computeFacts(document, key.body(), layout, inputs, projection.inputVariables(), projection.flow());
    for (var local : projection.locals()) {
      session.rememberIndependentFacts(new Key(key.body(), local, key.context()), facts, inputs, complete);
    }
    return facts;
  }

  private static @Nullable Projection plan(
    VariableFlowAnalyzer analyzer, DocumentContext document, FlowLayout layout,
    Key key, FlowInputs inputs
  ) {
    var body = key.body();
    var variable = key.variable();
    var variables = analyzer.variablesOf(document, body, layout, inputs);
    var flow = analyzer.flowGraph(document, body, inputs);
    var compiled = flow.compiled();
    var vertices = compiled == null ? flow.graph().vertexSet() : compiled.vertices();
    Predicate<ParserRuleContext> reachable = node -> compiled == null || possiblyReachable(node, layout, vertices);
    if (!IndependentReturnedStructure.supports(document, body, variable, variables, vertices, inputs, reachable)) {
      return null;
    }
    var locals = IndependentReturnedStructure.returnedLocals(document, body, variables, vertices, inputs,
      variable, reachable);
    return new Projection(flow, variables.forVariables(locals), locals);
  }

  private static boolean possiblyReachable(ParserRuleContext node, FlowLayout layout, Set<CfgVertex> vertices) {
    var statement = layout.enclosingStatement(node);
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
