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
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorFlow;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import com.google.common.base.Equivalence;
import java.util.List;
import java.util.Map;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Facts;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowInputs;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Change;

import static com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.MAX_PASSES;

/**
 * Один расчёт по телу: держит исходные данные и разложенное тело, чтобы не таскать их
 * через все шаги.
 */
final class VariableFlowPass {

  private final VariableFlowAnalyzer analyzer;
  private final DocumentContext documentContext;
  private final ControlFlowGraph graph;
  private final FlowLayout layout;
  private final FlowInputs inputs;
  /** Переменные тела по их номерам в окружении. */
  private final FlowVariables bodyVariables;
  /** Номера переменных, видимых другим телам: их тип сбрасывается на вызовах. */
  private final List<Integer> shared;
  /** Достижимая часть графа во всех возможных режимах; {@code null} отключает модель контекстов 1С. */
  private final @Nullable PreprocessorFlow compiledFlow;

  private final FlowAssignmentMemo assignments = new FlowAssignmentMemo();

  /**
   * Окружение перед каждым оператором. Заполняется по ходу расчёта, а не только в конце:
   * пока идут проходы, отсюда отвечают вложенные запросы про переменные этого же тела.
   */
  private final Map<ParserRuleContext, FlowEnvironment> beforeStatement = new IdentityHashMap<>();

  /** Окружение на входе в тело: считается по первому требованию и переиспользуется. */
  @Nullable
  private FlowEnvironment entry;

  /**
   * Окружение, с которым управление покидает тело: объединение значений перед каждым
   * вызовом. Нужно переменным, видимым другим телам: вызванный метод застаёт их
   * такими, какими они были в точке вызова.
   */
  private FlowEnvironment leaving = FlowEnvironment.EMPTY;

  /** Посчитанное окружение на выходе вершины и вход, для которого он посчитан. */
  private final Map<CfgVertex, FlowEnvironment> outputsByVertex = new IdentityHashMap<>();
  private final Map<CfgVertex, FlowEnvironment> outgoingInput = new IdentityHashMap<>();

  VariableFlowPass(
    VariableFlowAnalyzer analyzer,
    DocumentContext documentContext,
    ControlFlowGraph graph,
    FlowLayout layout,
    FlowInputs inputs,
    @Nullable PreprocessorFlow compiledFlow,
    FlowVariables bodyVariables
  ) {
    this.analyzer = analyzer;
    this.documentContext = documentContext;
    this.graph = graph;
    this.layout = layout;
    this.inputs = inputs;
    this.compiledFlow = compiledFlow;
    this.bodyVariables = bodyVariables;
    this.shared = new ArrayList<>(0);
    for (var i = 0; i < bodyVariables.variables().size(); i++) {
      if (inputs.sharedWithOtherBodies().test(bodyVariables.variables().get(i))) {
        shared.add(i);
      }
    }
  }

  /**
   * Окружение перед каждым оператором тела и окружение на выходе из него. Считается
   * один раз: сперва окружения по вершинам до неподвижной точки, затем один проход по
   * операторам каждой вершины.
   *
   * @return окружения тела.
   */
  Facts computeFacts() {
    var byVertex = computeEntryFacts();
    for (var vertex : layout.orderedVertices()) {
      if (compiledFlow != null && !compiledFlow.vertices().contains(vertex)) {
        continue;
      }
      // Через тот же кэш выхода: к концу поиска неподвижной точки он уже посчитан для
      // этих же входных окружений, и повторный проход по операторам не нужен — вместе с
      // ним не нужны и повторные вызовы колбэков мутаторов.
      outgoingOf(vertex, byVertex.getOrDefault(vertex, FlowEnvironment.EMPTY));
    }
    // Окружение на входе в вершину выхода — это и есть окружение на выходе из тела:
    // в неё сходятся все пути, включая Возврат из середины. Факта у неё может не быть
    // (тело пустое либо выход недостижим) — тогда с чем вошли, с тем и вышли.
    var exit = byVertex.getOrDefault(graph.getExitPoint(), entryEnvironment());
    return new Facts(bodyVariables.indexes(), beforeStatement, bodyVariables.changesByStatement(), exit.union(leaving));
  }

  /**
   * Тип переменной перед оператором по тому, что насчитано к этому моменту. Отвечает на
   * вложенный запрос из вывода типа чьего-то присваивания в этом же теле.
   *
   * @param statement оператор, перед которым нужен тип.
   * @param variable  переменная.
   * @return тип; {@code null}, если до этого оператора расчёт ещё не дошёл и ответа
   *     у него нет.
   */
  @Nullable
  TypeSet estimateAt(ParserRuleContext statement, VariableSymbol variable) {
    var index = bodyVariables.indexes().get(variable);
    if (index == null) {
      return null;
    }
    var environment = beforeStatement.get(statement);
    var type = environment == null ? null : environment.get(index);
    assignments.recordRead(statement, index, type);
    return type;
  }

  /**
   * Окружение на входе в каждую вершину. Расчёт идёт проходами по всем вершинам, пока
   * значения не перестанут меняться: так тип, приходящий по обратному ребру цикла,
   * доезжает до вершин, посещённых раньше него.
   */
  private Map<CfgVertex, FlowEnvironment> computeEntryFacts() {
    Map<CfgVertex, FlowEnvironment> facts = new IdentityHashMap<>();
    facts.put(graph.getEntryPoint(), entryEnvironment());

    for (var pass = 0; pass < MAX_PASSES; pass++) {
      var changed = false;
      for (var vertex : layout.orderedVertices()) {
        if (compiledFlow != null && !compiledFlow.vertices().contains(vertex)) {
          continue;
        }
        var incoming = joinOfPredecessors(vertex, facts);
        if (vertex == graph.getEntryPoint()) {
          incoming = incoming.union(entryEnvironment());
        }
        if (!incoming.equals(facts.get(vertex))) {
          facts.put(vertex, incoming);
          changed = true;
        }
      }
      if (!changed) {
        break;
      }
    }
    return facts;
  }

  /**
   * Окружение на входе в тело: что известно о переменных до первого присваивания.
   * Считается один раз — источники объявленного типа тянут разбор комментариев и
   * аннотаций, а спрашивают их в каждом проходе.
   */
  private FlowEnvironment entryEnvironment() {
    if (entry == null) {
      var current = FlowEnvironment.blank(bodyVariables.variables().size());
      for (var i = 0; i < bodyVariables.variables().size(); i++) {
        current = current.with(i, entryFactOf(bodyVariables.variables().get(i)), bodyVariables.variables().size());
      }
      entry = current;
    }
    return entry;
  }

  /**
   * Тип переменной на входе в это тело. У переменной, живущей в одном теле, это
   * объявленное о ней; у видимой другим телам — то, с чем из тел выходят.
   */
  private TypeSet entryFactOf(VariableSymbol variable) {
    return inputs.sharedWithOtherBodies().test(variable)
      ? analyzer.cellOf(documentContext, variable, inputs)
      : inputs.declaredFact().apply(variable);
  }

  /**
   * Объединение окружений на выходе всех предшественников вершины. По рёбрам веток
   * условия окружение проходит суженным: на истинной ветке верно само условие, на
   * ложной — его отрицание. Этим же получается сужение охранным предложением
   * ({@code Если Х = Неопределено Тогда Возврат; КонецЕсли;}) — до кода за условием
   * доходит только ложная ветка.
   */
  private FlowEnvironment joinOfPredecessors(CfgVertex vertex, Map<CfgVertex, FlowEnvironment> facts) {
    var joined = FlowEnvironment.EMPTY;
    for (var edge : graph.incomingEdgesOf(vertex)) {
      if (compiledFlow != null && !compiledFlow.edges().contains(edge)) {
        continue;
      }
      var predecessor = graph.getEdgeSource(edge);
      var atPredecessor = facts.get(predecessor);
      if (atPredecessor != null) {
        var outgoing = outgoingOf(predecessor, atPredecessor);
        joined = joined.union(FlowConditionNarrowing.narrow(predecessor, edge.getType(), outgoing,
          inputs.narrowing(), bodyVariables.variables(), bodyVariables.indexes()));
      }
    }
    return joined;
  }

  /**
   * Окружение на выходе вершины — то же, что на входе, но с применёнными операторами.
   * <p>
   * Считается один раз на каждое входное окружение, а не на каждое исходящее ребро.
   * Иначе операторы вершины проигрывались бы заново для каждого её последователя, а
   * вместе с ними — колбэки операторов-мутаторов, которые тянут разбор вызова и вывод
   * типа значения. Сравнение входа идёт по ссылке: окружения неизменяемы, и при
   * изменении на месте старого создаётся новое.
   *
   * @param vertex   вершина.
   * @param incoming окружение на входе в неё.
   * @return окружение на выходе.
   */
  private FlowEnvironment outgoingOf(CfgVertex vertex, FlowEnvironment incoming) {
    if (Equivalence.identity().equivalent(outgoingInput.get(vertex), incoming)) {
      return outputsByVertex.get(vertex);
    }
    var result = applyStatements(vertex, incoming);
    outgoingInput.put(vertex, incoming);
    outputsByVertex.put(vertex, result);
    return result;
  }

  /**
   * Применить к окружению все операторы вершины по порядку, попутно запоминая окружение
   * перед каждым из них: пока идут проходы, отсюда отвечают вложенные запросы.
   */
  private FlowEnvironment applyStatements(CfgVertex vertex, FlowEnvironment incoming) {
    var current = incoming;
    for (var statement : layout.statementsOf(vertex)) {
      beforeStatement.put(statement, current);
      current = applyStatement(statement, current);
    }
    return current;
  }

  /**
   * Изменение окружения одним оператором: присваивание задаёт тип переменной заново,
   * оператор-мутатор дополняет уже накопленный. Операторов без изменений — большинство,
   * и они окружение не трогают.
   */
  private FlowEnvironment applyStatement(ParserRuleContext statement, FlowEnvironment incoming) {
    var changes = bodyVariables.changesByStatement().get(statement);
    var current = incoming;
    if (changes != null) {
      for (var change : changes) {
        var variable = change.variable();
        var updated = change.definition()
          ? assignments.at(change, statement, beforeStatement, inputs.assigned())
          : inputs.mutations().apply(variable, change.position(), current.get(change.index()));
        current = current.with(change.index(), updated, bodyVariables.variables().size());
      }
    }
    return forgetSharedAfterCall(statement, changes, current);
  }

  /**
   * Вернуть к объединению по области видимости переменные, видимые другим телам,
   * если оператор содержит вызов.
   * <p>
   * Вызванный метод мог присвоить такой переменной что угодно, поэтому дальше по коду
   * известно только объединение по области видимости — оно же входной факт. Присваивание в самом
   * операторе сильнее: оно происходит после того, как вызов вернул управление.
   * Точность здесь оператора, а не выражения: если вызов и обращение стоят в одном
   * операторе, обращение считается случившимся до вызова.
   *
   * @param statement оператор.
   * @param changes   изменения этого оператора; {@code null}, если их нет.
   * @param current   окружение после изменений оператора.
   * @return окружение с возвращёнными к объединению переменными.
   */
  private FlowEnvironment forgetSharedAfterCall(
    ParserRuleContext statement,
    @Nullable List<Change> changes,
    FlowEnvironment current
  ) {
    if (shared.isEmpty() || !layout.hasCall(statement)) {
      return current;
    }
    // То, что переменная содержит перед вызовом, вызванный метод видит — значит это
    // часть её значения на входе в другие тела, наравне со значением на выходе.
    leaving = leaving.union(current);
    var result = current;
    for (var index : shared) {
      if (assignedBy(changes, index)) {
        continue;
      }
      result = result.with(index, entryEnvironment().get(index), bodyVariables.variables().size());
    }
    return result;
  }

  /** Задаёт ли оператор тип переменной заново. */
  private static boolean assignedBy(@Nullable List<Change> changes, int index) {
    if (changes == null) {
      return false;
    }
    for (var change : changes) {
      if (change.index() == index && change.definition()) {
        return true;
      }
    }
    return false;
  }
}
