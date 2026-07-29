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
import com.github._1c_syntax.bsl.languageserver.cfg.CfgEdgeType;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ConditionalVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.cfg.WhileLoopVertex;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Тип переменной в конкретной точке кода — расчёт по графу потока управления тела.
 * <p>
 * Отвечает на вопрос «какой тип у переменной здесь», в отличие от объединения по всем
 * присваиваниям во всей области видимости. Присваивание перекрывает прежний тип,
 * оператор-мутатор ({@code Х.Вставить(…)}) дополняет накопленный, а в точках слияния
 * путей типы объединяются, поэтому
 * <pre>
 *   X = 10;
 *   ...        // здесь Число
 *   X = Истина;
 *   ...        // здесь Булево
 * </pre>
 * даёт разные ответы в разных местах.
 * <p>
 * Единица расчёта — <b>тело целиком</b>: один поиск неподвижной точки считает
 * {@link Environment окружение} — тип каждой переменной тела в каждой его точке. Поэтому
 * запрос про любую переменную тела — чтение готового ответа, а не отдельный расчёт.
 * <p>
 * Про присваиваемые типы, изменения мутаторов и сужение по условиям анализатор не знает
 * ничего — их отдают колбэки {@link FlowInputs}, а места изменений передаются готовыми
 * списками позиций. Поэтому здесь нет ни вывода типов выражений, ни индекса ссылок.
 * <p>
 * Кэшируется двоякое, и оба кэша сбрасываются по жизненному циклу документа: разложенное
 * по позициям тело и рассчитанные по нему окружения. Без второго неподвижная точка
 * считалась бы заново на каждый запрос типа, а диагностики спрашивают тип на каждое
 * обращение к члену.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class VariableFlowAnalyzer extends AbstractDocumentLifecycleClearableIndex {

  /**
   * Предел проходов по графу. Тип, приходящий по обратному ребру цикла, доезжает до
   * ранее посещённых вершин за считанные проходы; предел — страховка от незавершающегося
   * расчёта на неожиданной форме графа.
   */
  private static final int MAX_PASSES = 16;

  private final ControlFlowGraphIndex controlFlowGraphIndex;

  private final Map<URI, Map<BSLParser.CodeBlockContext, FlowLayout>> layoutsByUri = new ConcurrentHashMap<>();
  private final Map<URI, Map<BSLParser.CodeBlockContext, Facts>> factsByUri = new ConcurrentHashMap<>();

  /**
   * Тип каждой переменной тела в одной его точке.
   * <p>
   * Неизменяемо, поэтому соседние точки, между которыми ничего не поменялось, ссылаются на
   * одно окружение. За счёт этого хранение растёт по числу мест, где тип меняется, а не по
   * числу операторов, помноженному на число переменных.
   *
   * @param types типы переменных; переменных, о которых ничего не известно, в карте нет.
   */
  private record Environment(Map<VariableSymbol, TypeSet> types) {

    private static final Environment EMPTY = new Environment(Map.of());

    /** Тип переменной; пустой набор, если про неё здесь ничего не известно. */
    TypeSet get(VariableSymbol variable) {
      return types.getOrDefault(variable, TypeSet.EMPTY);
    }

    /** Окружение с изменённым типом одной переменной. */
    Environment with(VariableSymbol variable, TypeSet type) {
      if (type.equals(get(variable))) {
        return this;
      }
      var changed = new HashMap<>(types);
      changed.put(variable, type);
      return new Environment(changed);
    }

    /**
     * Объединение с другим окружением: тип каждой переменной — объединение её типов.
     * Так сходятся пути в точке слияния.
     */
    Environment union(Environment other) {
      if (types.isEmpty()) {
        return other;
      }
      if (other.types.isEmpty()) {
        return this;
      }
      var merged = new HashMap<>(types);
      other.types.forEach((variable, type) -> merged.merge(variable, type, TypeSet::union));
      return new Environment(merged);
    }

    /**
     * Окружение, к каждой переменной которого применено преобразование, — так работает
     * сужение по охраняющему условию на ребре ветки.
     */
    Environment map(Function<VariableSymbol, TypeSet> transform) {
      if (types.isEmpty()) {
        return this;
      }
      Map<VariableSymbol, TypeSet> mapped = new HashMap<>(types.size());
      types.forEach((variable, type) -> mapped.put(variable, transform.apply(variable)));
      return new Environment(mapped);
    }
  }

  /**
   * Изменение типа одной переменной одним оператором.
   *
   * @param variable   переменная.
   * @param position   позиция присваивания либо оператора-мутатора.
   * @param definition {@code true} — присваивание (задаёт тип заново),
   *                   {@code false} — мутатор (дополняет накопленный).
   */
  private record Change(VariableSymbol variable, Position position, boolean definition) {
  }

  /**
   * Рассчитанное по телу, что не зависит от точки запроса.
   *
   * @param applicable        переменные, для которых расчёт по потоку применим; про
   *                          остальные отвечает прежний путь с обходом области видимости.
   * @param beforeStatement   окружение перед каждым оператором тела.
   * @param changesByStatement изменения типов по операторам, в которых они стоят.
   */
  private record Facts(
    Set<VariableSymbol> applicable,
    Map<ParserRuleContext, Environment> beforeStatement,
    Map<ParserRuleContext, List<Change>> changesByStatement
  ) {

    /** Изменение типа переменной в этом операторе; {@code null}, если его тут нет. */
    @Nullable
    Change changeOf(ParserRuleContext statement, VariableSymbol variable) {
      for (var change : changesByStatement.getOrDefault(statement, List.of())) {
        if (change.variable() == variable) {
          return change;
        }
      }
      return null;
    }
  }

  /**
   * Присваиваемые переменной типы в точке присваивания.
   */
  @FunctionalInterface
  public interface AssignedTypes {

    /**
     * Типы, которые переменная получает в этом присваивании.
     *
     * @param variable  переменная, которой присваивают.
     * @param statement оператор графа, в котором стоит присваивание. Передаётся, чтобы
     *                  вызывающему не пришлось искать его спуском по дереву разбора:
     *                  расчёт уже знает этот узел.
     * @param position  позиция присваивания — начало имени переменной в левой части.
     * @return присваиваемые типы; пустой набор, если вывести их не удалось.
     */
    TypeSet at(VariableSymbol variable, ParserRuleContext statement, Position position);
  }

  /**
   * Изменение типа оператором-мутатором: {@code Х.Вставить("Поле", …)},
   * {@code Х.Колонки.Добавить("Имя", …)} и подобными.
   */
  @FunctionalInterface
  public interface Mutations {

    /**
     * Как оператор в этой позиции меняет тип переменной.
     *
     * @param variable переменная-получатель.
     * @param position позиция оператора-мутатора.
     * @param incoming тип переменной перед ним.
     * @return изменённый тип; исходный, если оператор ничего не добавляет.
     */
    TypeSet apply(VariableSymbol variable, Position position, TypeSet incoming);
  }

  /**
   * Сужение типа охраняющим условием на ветке.
   */
  @FunctionalInterface
  public interface GuardNarrowing {

    /**
     * Как условие сужает тип переменной на своей ветке.
     *
     * @param variable  переменная, тип которой сужается.
     * @param condition выражение условия.
     * @param whenTrue  ветка: {@code true} — истинная, {@code false} — ложная.
     * @param incoming  тип переменной перед условием.
     * @return суженный тип; исходный, если условие про эту переменную ничего не утверждает.
     */
    TypeSet narrow(VariableSymbol variable, BSLParser.ExpressionContext condition, boolean whenTrue, TypeSet incoming);
  }

  /**
   * Исходные данные расчёта по телу.
   *
   * @param cacheable           можно ли запоминать результат: вложенный расчёт мог быть
   *                            усечён защитой от циклов, такой результат переиспользовать нельзя.
   * @param variables           переменные тела, за типами которых следит расчёт.
   * @param entryFact           тип переменной на входе в тело: объявленные типы параметра,
   *                            типы из аннотаций и прочее, что известно до первого присваивания.
   * @param definitionPositions позиции всех присваиваний переменной в документе;
   *                            учитываются только попавшие в это тело.
   * @param mutationPositions   позиции операторов, меняющих тип переменной на месте.
   * @param assigned            колбэк, отдающий присваиваемые типы по позиции.
   * @param mutations           колбэк, применяющий изменение оператора-мутатора.
   * @param narrowing           колбэк, сужающий тип по охраняющему условию ветки.
   *     <p>
   *     Переменные и места изменений отдаются поставщиками, а не значениями: за ними стоит
   *     обход индекса ссылок и разбор документирующих комментариев. При готовом ответе в
   *     кэше ничего этого не нужно, а тип спрашивают на каждое обращение к переменной.
   */
  public record FlowInputs(
    boolean cacheable,
    Supplier<Collection<VariableSymbol>> variables,
    Function<VariableSymbol, TypeSet> entryFact,
    Function<VariableSymbol, Collection<Position>> definitionPositions,
    Function<VariableSymbol, Collection<Position>> mutationPositions,
    AssignedTypes assigned,
    Mutations mutations,
    GuardNarrowing narrowing
  ) {
  }

  /**
   * Тип переменной в точке использования, когда узел использования уже под рукой —
   * например в разборе выражения. Тело и оператор находятся подъёмом по дереву от узла.
   *
   * @param documentContext контекст документа с использованием.
   * @param use             узел использования переменной.
   * @param variable        переменная, тип которой нужен.
   * @param inputs          исходные данные расчёта.
   * @return тип переменной в этой точке; {@code null}, если расчёт неприменим —
   *     тело не найдено или использование не удалось разместить в графе.
   */
  @Nullable
  public TypeSet typeAt(
    DocumentContext documentContext,
    ParserRuleContext use,
    VariableSymbol variable,
    FlowInputs inputs
  ) {
    var body = enclosingBody(use);
    if (body == null) {
      return null;
    }
    var layout = layoutOf(documentContext, body);
    var useStatement = enclosingStatement(use, layout);
    if (useStatement == null) {
      return null;
    }
    return typeAtStatement(documentContext, body, layout, useStatement, use, false, variable, inputs);
  }

  /**
   * Тип переменной в позиции документа — для вызывающих, у которых узла нет, а есть
   * только позиция (ссылка из индекса, точка курсора). Тело метода ищется перебором
   * объявлений верхнего уровня, оператор — по разложенному телу: спуска по дереву тут нет.
   *
   * @param documentContext контекст документа с использованием.
   * @param position        позиция использования.
   * @param atDefinition    стоит ли позиция на присваивании переменной: тогда тип берётся
   *                        после этого присваивания, а не до него.
   * @param variable        переменная, тип которой нужен.
   * @param inputs          исходные данные расчёта.
   * @return тип переменной в этой точке; {@code null}, если расчёт неприменим.
   */
  @Nullable
  public TypeSet typeAt(
    DocumentContext documentContext,
    Position position,
    boolean atDefinition,
    VariableSymbol variable,
    FlowInputs inputs
  ) {
    var body = bodyAt(documentContext, position);
    if (body == null) {
      return null;
    }
    var layout = layoutOf(documentContext, body);
    var useStatement = layout.statementAt(position);
    if (useStatement == null) {
      return null;
    }
    return typeAtStatement(documentContext, body, layout, useStatement, null, atDefinition, variable, inputs);
  }

  /**
   * Удалить кэши по URI документа.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    layoutsByUri.remove(uri);
    factsByUri.remove(uri);
  }

  /**
   * Разложенное тело — из кэша либо посчитанное на месте.
   * <p>
   * Считается <b>вне</b> {@code computeIfAbsent}: разбор тела не мгновенный, а под замком
   * корзины на нём выстраивались бы все потоки пакетного анализа. Двойная работа при гонке
   * безвредна — раскладка от расчёта не зависит.
   */
  private FlowLayout layoutOf(DocumentContext documentContext, BSLParser.CodeBlockContext body) {
    var byBody = layoutsByUri.computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>());
    var cached = byBody.get(body);
    if (cached != null) {
      return cached;
    }
    var graph = controlFlowGraphIndex.graphOf(documentContext, body, CfgBuildOptions.defaults());
    var layout = FlowLayout.of(graph, body);
    var previous = byBody.putIfAbsent(body, layout);
    return previous == null ? layout : previous;
  }

  /** Общая часть обоих входов: проверки применимости и чтение окружения. */
  @Nullable
  private TypeSet typeAtStatement(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    ParserRuleContext useStatement,
    @Nullable ParserRuleContext useNode,
    boolean atDefinition,
    VariableSymbol variable,
    FlowInputs inputs
  ) {
    if (layout.vertexOf(useStatement) == null) {
      return null;
    }
    var facts = factsOf(documentContext, body, layout, inputs);
    if (!facts.applicable().contains(variable)) {
      return null;
    }
    var change = facts.changeOf(useStatement, variable);
    var before = facts.beforeStatement().getOrDefault(useStatement, Environment.EMPTY).get(variable);
    var inclusive = useNode == null
      ? atDefinition
      : change != null && change.definition() && Ranges.containsPosition(Ranges.create(useNode), change.position());
    if (!inclusive || change == null) {
      return before;
    }
    // Использование стоит на самом изменении — нужен тип после него.
    return change.definition()
      ? inputs.assigned().at(variable, useStatement, change.position())
      : inputs.mutations().apply(variable, change.position(), before);
  }

  /**
   * Рассчитанные по телу окружения — из кэша либо посчитанные на месте. Окружения не
   * зависят от точки запроса, поэтому одного расчёта хватает на все обращения ко всем
   * переменным тела.
   */
  private Facts factsOf(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    FlowInputs inputs
  ) {
    var byBody = inputs.cacheable()
      ? factsByUri.computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>())
      : null;
    if (byBody != null) {
      var cached = byBody.get(body);
      if (cached != null) {
        return cached;
      }
    }
    // Расчёт идёт ВНЕ computeIfAbsent: он не мгновенный, и под замком корзины на нём
    // выстраивались бы все потоки пакетного анализа. Вдобавок расчёт тянет вывод типов,
    // который может попросить тип переменной другого тела того же документа, — это
    // рекурсивное обновление той же карты, что запрещено.
    var computed = compute(documentContext, body, layout, inputs);
    if (byBody == null) {
      return computed;
    }
    var previous = byBody.putIfAbsent(body, computed);
    return previous == null ? computed : previous;
  }

  /** Собрать изменения типов по операторам и посчитать по ним окружения тела. */
  private Facts compute(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    FlowInputs inputs
  ) {
    Set<VariableSymbol> applicable = Collections.newSetFromMap(new IdentityHashMap<>());
    Map<ParserRuleContext, List<Change>> changesByStatement = new IdentityHashMap<>();
    for (var variable : inputs.variables().get()) {
      var definitions = inputs.definitionPositions().apply(variable);
      var mutations = inputs.mutationPositions().apply(variable);
      // Присваиваний нет — расчёту нечего перекрывать; а если хоть одно изменение типа не
      // легло в граф отдельным оператором, его вклад потерялся бы. И там, и там точнее
      // прежний путь с обходом всей области видимости.
      if (definitions.isEmpty() || !allPlaced(layout, definitions) || !allPlaced(layout, mutations)) {
        continue;
      }
      applicable.add(variable);
      collectChanges(layout, variable, definitions, true, changesByStatement);
      collectChanges(layout, variable, mutations, false, changesByStatement);
    }
    if (applicable.isEmpty()) {
      return new Facts(applicable, Map.of(), Map.of());
    }
    var graph = controlFlowGraphIndex.graphOf(documentContext, body, CfgBuildOptions.defaults());
    var pass = new Pass(graph, layout, inputs, applicable, changesByStatement);
    return new Facts(applicable, pass.computeStatementFacts(), changesByStatement);
  }

  /** Разложить позиции изменений по операторам, в которых они стоят. */
  private static void collectChanges(
    FlowLayout layout,
    VariableSymbol variable,
    Collection<Position> positions,
    boolean definition,
    Map<ParserRuleContext, List<Change>> target
  ) {
    layout.index(positions).forEach((statement, position) ->
      target.computeIfAbsent(statement, key -> new ArrayList<>(1))
        .add(new Change(variable, position, definition)));
  }

  /** Все ли изменения типа легли в операторы графа. */
  private static boolean allPlaced(FlowLayout layout, Collection<Position> positions) {
    for (var position : positions) {
      if (layout.insideBody(position) && !layout.isPlaced(position)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Один расчёт по телу: держит исходные данные и разложенное тело, чтобы не таскать их
   * через все шаги.
   */
  private static final class Pass {

    private final ControlFlowGraph graph;
    private final FlowLayout layout;
    private final FlowInputs inputs;
    private final Set<VariableSymbol> applicable;
    private final Map<ParserRuleContext, List<Change>> changesByStatement;

    /**
     * Присваиваемые типы, уже посчитанные в этом расчёте. Каждый проход до неподвижной
     * точки заново проходит по тем же присваиваниям, а вывод типа выражения справа —
     * самая дорогая часть шага.
     */
    private final Map<Position, TypeSet> assignedTypes = new HashMap<>();

    private Pass(
      ControlFlowGraph graph,
      FlowLayout layout,
      FlowInputs inputs,
      Set<VariableSymbol> applicable,
      Map<ParserRuleContext, List<Change>> changesByStatement
    ) {
      this.graph = graph;
      this.layout = layout;
      this.inputs = inputs;
      this.applicable = applicable;
      this.changesByStatement = changesByStatement;
    }

    /**
     * Окружение перед каждым оператором тела. Считается один раз: сперва окружения по
     * вершинам до неподвижной точки, затем один проход по операторам каждой вершины.
     *
     * @return окружение перед каждым оператором графа.
     */
    private Map<ParserRuleContext, Environment> computeStatementFacts() {
      var byVertex = computeEntryFacts();
      Map<ParserRuleContext, Environment> beforeStatement = new IdentityHashMap<>();
      for (var vertex : layout.orderedVertices()) {
        var current = byVertex.getOrDefault(vertex, Environment.EMPTY);
        for (var statement : layout.statementsOf(vertex)) {
          beforeStatement.put(statement, current);
          current = applyStatement(statement, current);
        }
      }
      return beforeStatement;
    }

    /**
     * Окружение на входе в каждую вершину. Расчёт идёт проходами по всем вершинам, пока
     * значения не перестанут меняться: так тип, приходящий по обратному ребру цикла,
     * доезжает до вершин, посещённых раньше него.
     */
    private Map<CfgVertex, Environment> computeEntryFacts() {
      Map<CfgVertex, Environment> facts = new IdentityHashMap<>();
      facts.put(graph.getEntryPoint(), entryEnvironment());

      for (var pass = 0; pass < MAX_PASSES; pass++) {
        var changed = false;
        for (var vertex : layout.orderedVertices()) {
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

    /** Окружение на входе в тело: что известно о переменных до первого присваивания. */
    private Environment entryEnvironment() {
      Map<VariableSymbol, TypeSet> entry = new HashMap<>();
      for (var variable : applicable) {
        var types = inputs.entryFact().apply(variable);
        if (!types.isEmpty()) {
          entry.put(variable, types);
        }
      }
      return entry.isEmpty() ? Environment.EMPTY : new Environment(entry);
    }

    /**
     * Объединение окружений на выходе всех предшественников вершины. По рёбрам веток
     * условия окружение проходит суженным: на истинной ветке верно само условие, на
     * ложной — его отрицание. Этим же получается сужение охранным предложением
     * ({@code Если Х = Неопределено Тогда Возврат; КонецЕсли;}) — до кода за условием
     * доходит только ложная ветка.
     */
    private Environment joinOfPredecessors(CfgVertex vertex, Map<CfgVertex, Environment> facts) {
      var joined = Environment.EMPTY;
      for (var edge : graph.incomingEdgesOf(vertex)) {
        var predecessor = graph.getEdgeSource(edge);
        var atPredecessor = facts.get(predecessor);
        if (atPredecessor != null) {
          var outgoing = applyStatements(predecessor, atPredecessor);
          joined = joined.union(narrowedByEdge(predecessor, edge.getType(), outgoing));
        }
      }
      return joined;
    }

    /**
     * Окружение, прошедшее по ребру ветки условия. Условие цикла {@code Пока} сужает так
     * же, как условие {@code Если}: на входе в тело оно верно, за циклом — ложно.
     */
    private Environment narrowedByEdge(CfgVertex predecessor, CfgEdgeType edgeType, Environment outgoing) {
      if (edgeType != CfgEdgeType.TRUE_BRANCH && edgeType != CfgEdgeType.FALSE_BRANCH) {
        return outgoing;
      }
      var condition = conditionOf(predecessor);
      if (condition == null) {
        return outgoing;
      }
      var whenTrue = edgeType == CfgEdgeType.TRUE_BRANCH;
      return outgoing.map(variable ->
        inputs.narrowing().narrow(variable, condition, whenTrue, outgoing.get(variable)));
    }

    /** Условие вершины-ветвления; {@code null}, если вершина условия не несёт. */
    private static BSLParser.@Nullable ExpressionContext conditionOf(CfgVertex vertex) {
      if (vertex instanceof ConditionalVertex conditional) {
        return conditional.getExpression();
      }
      return vertex instanceof WhileLoopVertex loop ? loop.getExpression() : null;
    }

    /** Применить к окружению все операторы вершины по порядку. */
    private Environment applyStatements(CfgVertex vertex, Environment incoming) {
      var current = incoming;
      for (var statement : layout.statementsOf(vertex)) {
        current = applyStatement(statement, current);
      }
      return current;
    }

    /**
     * Изменение окружения одним оператором: присваивание задаёт тип переменной заново,
     * оператор-мутатор дополняет уже накопленный. Операторов без изменений — большинство,
     * и они окружение не трогают.
     */
    private Environment applyStatement(ParserRuleContext statement, Environment incoming) {
      var changes = changesByStatement.get(statement);
      if (changes == null) {
        return incoming;
      }
      var current = incoming;
      for (var change : changes) {
        var variable = change.variable();
        var updated = change.definition()
          ? assignedTypes.computeIfAbsent(
            change.position(), position -> inputs.assigned().at(variable, statement, position))
          : inputs.mutations().apply(variable, change.position(), current.get(variable));
        current = current.with(variable, updated);
      }
      return current;
    }
  }

  /**
   * Тело метода или код модуля, которому принадлежит узел, — единица, по которой строится
   * граф потока управления. Это самый внешний блок кода над узлом: вложенные блоки
   * ветвлений и циклов частями графа не являются.
   */
  private static BSLParser.@Nullable CodeBlockContext enclosingBody(ParserRuleContext node) {
    ParserRuleContext current = node;
    BSLParser.CodeBlockContext outermost = null;
    while (current != null) {
      if (current instanceof BSLParser.CodeBlockContext codeBlock) {
        outermost = codeBlock;
      }
      current = current.getParent();
    }
    return outermost;
  }

  /** Ближайший оператор графа, внутри которого находится узел. */
  @Nullable
  private static ParserRuleContext enclosingStatement(ParserRuleContext node, FlowLayout layout) {
    ParserRuleContext current = node;
    while (current != null) {
      if (layout.hasStatement(current)) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  /**
   * Тело метода или код модуля, накрывающие позицию. Перебираются только объявления
   * верхнего уровня — в операторы разбор не спускается.
   */
  private static BSLParser.@Nullable CodeBlockContext bodyAt(DocumentContext documentContext, Position position) {
    var ast = documentContext.getAst();
    var subs = ast.subs();
    if (subs != null) {
      for (var sub : subs.sub()) {
        if (!Ranges.containsPosition(Ranges.create(sub), position)) {
          continue;
        }
        var procedure = sub.procedure();
        if (procedure != null) {
          return procedure.subCodeBlock().codeBlock();
        }
        var function = sub.function();
        return function == null ? null : function.subCodeBlock().codeBlock();
      }
    }
    var fileCodeBlock = ast.fileCodeBlock();
    if (fileCodeBlock == null) {
      return null;
    }
    // Тело модуля в дереве может быть не одно (код до и после объявлений методов), и
    // добраться отсюда можно не до всякого. Отдаём блок, только если он правда накрывает
    // позицию: иначе расчёт пошёл бы по чужому телу.
    var codeBlock = fileCodeBlock.codeBlock();
    return codeBlock != null && Ranges.containsPosition(Ranges.create(codeBlock), position) ? codeBlock : null;
  }
}
