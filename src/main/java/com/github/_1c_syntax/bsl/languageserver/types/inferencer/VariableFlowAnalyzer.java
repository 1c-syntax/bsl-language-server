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

import com.github._1c_syntax.bsl.languageserver.cfg.BasicBlockVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgEdgeType;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ConditionalVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Тип переменной в конкретной точке кода — расчёт по графу потока управления метода.
 * <p>
 * Отвечает на вопрос «какой тип у переменной здесь», в отличие от объединения по всем
 * присваиваниям во всей области видимости. Присваивание перекрывает прежний тип, а в
 * точках слияния путей типы объединяются, поэтому
 * <pre>
 *   X = 10;
 *   ...        // здесь Число
 *   X = Истина;
 *   ...        // здесь Булево
 * </pre>
 * даёт разные ответы в разных местах.
 * <p>
 * Расчёт ведётся <b>по одной переменной</b>: остальные операторы для неё прозрачны.
 * Это делает разбор соразмерным запросу — обычно спрашивают про одну-две переменные,
 * а не про всё окружение метода целиком.
 * <p>
 * Про присваиваемые типы анализатор не знает ничего: их отдаёт колбэк
 * {@link AssignedTypes}, а места присваиваний передаются готовым списком позиций.
 * Поэтому здесь нет ни вывода типов выражений, ни индекса ссылок.
 */
@Component
@RequiredArgsConstructor
public class VariableFlowAnalyzer {

  /**
   * Предел проходов по графу. Тип, приходящий по обратному ребру цикла, доезжает до
   * ранее посещённых вершин за считанные проходы; предел — страховка от незавершающегося
   * расчёта на неожиданной форме графа.
   */
  private static final int MAX_PASSES = 16;

  private final ControlFlowGraphIndex controlFlowGraphIndex;

  /**
   * Присваиваемые переменной типы в точке присваивания.
   */
  @FunctionalInterface
  public interface AssignedTypes {

    /**
     * Типы, которые переменная получает в этом присваивании.
     *
     * @param position позиция присваивания — начало имени переменной в левой части.
     * @return присваиваемые типы; пустой набор, если вывести их не удалось.
     */
    TypeSet at(Position position);
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
     * @param position позиция оператора-мутатора.
     * @param incoming тип переменной перед ним.
     * @return изменённый тип; исходный, если оператор ничего не добавляет.
     */
    TypeSet apply(Position position, TypeSet incoming);
  }

  /**
   * Сужение типа охраняющим условием на ветке.
   */
  @FunctionalInterface
  public interface GuardNarrowing {

    /**
     * Как условие сужает тип переменной на своей ветке.
     *
     * @param condition выражение условия.
     * @param whenTrue  ветка: {@code true} — истинная, {@code false} — ложная.
     * @param incoming  тип переменной перед условием.
     * @return суженный тип; исходный, если условие про эту переменную ничего не утверждает.
     */
    TypeSet narrow(BSLParser.ExpressionContext condition, boolean whenTrue, TypeSet incoming);
  }

  /**
   * Исходные данные расчёта по одной переменной.
   *
   * @param entryFact           тип переменной на входе в тело: объявленные типы параметра,
   *                            типы из аннотаций и прочее, что известно до первого присваивания.
   * @param definitionPositions позиции всех присваиваний переменной в документе;
   *                            учитываются только попавшие в анализируемое тело.
   * @param mutationPositions   позиции операторов, меняющих тип переменной на месте
   *                            (добавление поля структуры, колонки таблицы значений).
   * @param assigned            колбэк, отдающий присваиваемые типы по позиции.
   * @param mutations           колбэк, применяющий изменение оператора-мутатора.
   * @param narrowing           колбэк, сужающий тип по охраняющему условию ветки.
   */
  public record FlowInputs(
    TypeSet entryFact,
    Collection<Position> definitionPositions,
    Collection<Position> mutationPositions,
    AssignedTypes assigned,
    Mutations mutations,
    GuardNarrowing narrowing
  ) {
  }

  /**
   * Тип переменной в точке использования, когда узел использования уже под рукой —
   * например в разборе выражения. Тело и оператор находятся подъёмом по дереву от узла,
   * без поиска по диапазонам.
   *
   * @param documentContext контекст документа с использованием.
   * @param use             узел использования переменной.
   * @param inputs          исходные данные расчёта.
   * @return тип переменной в этой точке; {@code null}, если расчёт неприменим —
   *     тело не найдено или использование не удалось разместить в графе.
   */
  @Nullable
  public TypeSet typeAt(DocumentContext documentContext, ParserRuleContext use, FlowInputs inputs) {
    var body = enclosingBody(use);
    if (body == null) {
      return null;
    }
    var graph = controlFlowGraphIndex.graphOf(documentContext, body, CfgBuildOptions.defaults());
    var vertexByStatement = vertexByStatement(graph);
    var useStatement = enclosingStatement(use, vertexByStatement.keySet());
    // Использование в левой части присваивания — это само присваивание: спрашивают тип,
    // который переменная получает здесь, а не тот, что был до неё.
    var atDefinition = coversAnyDefinition(use, inputs.definitionPositions());
    return typeAtStatement(graph, body, vertexByStatement, useStatement, atDefinition, inputs);
  }

  /**
   * Тип переменной в позиции документа — для вызывающих, у которых узла нет, а есть
   * только позиция (ссылка из индекса). Тело метода ищется перебором объявлений верхнего
   * уровня по диапазонам, оператор — перебором операторов графа: спуска по дереву разбора
   * тут нет, поэтому вызов по стоимости сопоставим с узловым.
   *
   * @param documentContext контекст документа с использованием.
   * @param position        позиция использования.
   * @param atDefinition    стоит ли позиция на присваивании переменной: тогда тип берётся
   *                        после этого присваивания, а не до него.
   * @param inputs          исходные данные расчёта.
   * @return тип переменной в этой точке; {@code null}, если расчёт неприменим.
   */
  @Nullable
  public TypeSet typeAt(
    DocumentContext documentContext,
    Position position,
    boolean atDefinition,
    FlowInputs inputs
  ) {
    var body = bodyAt(documentContext, position);
    if (body == null) {
      return null;
    }
    var graph = controlFlowGraphIndex.graphOf(documentContext, body, CfgBuildOptions.defaults());
    var vertexByStatement = vertexByStatement(graph);
    var useStatement = statementAt(vertexByStatement.keySet(), position);
    return typeAtStatement(graph, body, vertexByStatement, useStatement, atDefinition, inputs);
  }

  /** Общая часть обоих входов: проверки применимости и сам расчёт. */
  @Nullable
  private static TypeSet typeAtStatement(
    ControlFlowGraph graph,
    BSLParser.CodeBlockContext body,
    Map<ParserRuleContext, CfgVertex> vertexByStatement,
    @Nullable ParserRuleContext useStatement,
    boolean atDefinition,
    FlowInputs inputs
  ) {
    if (useStatement == null) {
      return null;
    }
    // Если хоть один меняющий тип оператор не лёг в граф, расчёт по потоку потерял бы его
    // вклад — тогда точнее прежний путь с обходом всей области видимости.
    if (!allPlaced(vertexByStatement.keySet(), body, inputs.definitionPositions())
      || !allPlaced(vertexByStatement.keySet(), body, inputs.mutationPositions())) {
      return null;
    }
    var useVertex = vertexByStatement.get(useStatement);
    if (useVertex == null) {
      return null;
    }
    return new Pass(
      graph,
      inputs.entryFact(),
      inputs.definitionPositions(),
      inputs.mutationPositions(),
      inputs.assigned(),
      inputs.mutations(),
      inputs.narrowing()
    ).typeAtStatement(useVertex, useStatement, atDefinition);
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
    return fileCodeBlock == null ? null : fileCodeBlock.codeBlock();
  }

  /** Оператор графа, накрывающий позицию. */
  @Nullable
  private static ParserRuleContext statementAt(Collection<ParserRuleContext> statements, Position position) {
    for (var statement : statements) {
      if (Ranges.containsPosition(Ranges.create(statement), position)) {
        return statement;
      }
    }
    return null;
  }

  /**
   * Один расчёт по одной переменной: держит исходные данные, чтобы не таскать их
   * через все шаги.
   */
  @RequiredArgsConstructor
  private static final class Pass {

    private final ControlFlowGraph graph;
    private final TypeSet entryFact;
    private final Collection<Position> definitionPositions;
    private final Collection<Position> mutationPositions;
    private final AssignedTypes assigned;
    private final Mutations mutations;
    private final GuardNarrowing narrowing;

    /**
     * Присваиваемые типы, уже посчитанные в этом расчёте. Каждый проход до неподвижной
     * точки заново проходит по тем же присваиваниям, а вывод типа выражения справа —
     * самая дорогая часть шага.
     */
    private final Map<Position, TypeSet> assignedTypes = new HashMap<>();

    /**
     * Тип переменной в указанном операторе внутри его вершины.
     *
     * @param inclusive учитывать ли присваивание в самом этом операторе.
     */
    private TypeSet typeAtStatement(CfgVertex vertex, ParserRuleContext statement, boolean inclusive) {
      var facts = computeEntryFacts();
      var atVertex = facts.getOrDefault(vertex, TypeSet.EMPTY);
      return applyStatements(vertex, atVertex, statement, inclusive);
    }

    /**
     * Тип переменной на входе в каждую вершину. Расчёт идёт проходами по всем вершинам,
     * пока значения не перестанут меняться: так тип, приходящий по обратному ребру
     * цикла, доезжает до вершин, посещённых раньше него.
     */
    private Map<CfgVertex, TypeSet> computeEntryFacts() {
      var ordered = orderedVertices();
      Map<CfgVertex, TypeSet> facts = new HashMap<>();
      facts.put(graph.getEntryPoint(), entryFact);

      for (var pass = 0; pass < MAX_PASSES; pass++) {
        var changed = false;
        for (var vertex : ordered) {
          var incoming = joinOfPredecessors(vertex, facts);
          if (vertex == graph.getEntryPoint()) {
            incoming = incoming.union(entryFact);
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
     * Объединение типов на выходе всех предшественников вершины. По рёбрам веток
     * условия тип проходит суженным: на истинной ветке верно само условие, на ложной —
     * его отрицание. Этим же получается сужение охранным предложением
     * ({@code Если Х = Неопределено Тогда Возврат; КонецЕсли;}) — до кода за условием
     * доходит только ложная ветка.
     */
    private TypeSet joinOfPredecessors(CfgVertex vertex, Map<CfgVertex, TypeSet> facts) {
      var joined = TypeSet.EMPTY;
      for (var edge : graph.incomingEdgesOf(vertex)) {
        var predecessor = graph.getEdgeSource(edge);
        var atPredecessor = facts.get(predecessor);
        if (atPredecessor != null) {
          var outgoing = applyStatements(predecessor, atPredecessor, null, false);
          joined = joined.union(narrowedByEdge(predecessor, edge.getType(), outgoing));
        }
      }
      return joined;
    }

    /** Тип, прошедший по ребру ветки условия. */
    private TypeSet narrowedByEdge(CfgVertex predecessor, CfgEdgeType edgeType, TypeSet outgoing) {
      if (!(predecessor instanceof ConditionalVertex conditional)) {
        return outgoing;
      }
      if (edgeType != CfgEdgeType.TRUE_BRANCH && edgeType != CfgEdgeType.FALSE_BRANCH) {
        return outgoing;
      }
      return narrowing.narrow(conditional.getExpression(), edgeType == CfgEdgeType.TRUE_BRANCH, outgoing);
    }

    /**
     * Применить к типу операторы вершины по порядку. Если задан {@code stopAt},
     * обход останавливается на этом операторе: {@code X = F(X)} читает прежний тип,
     * поэтому по умолчанию оператор, на котором остановились, не применяется. Флаг
     * {@code inclusive} применяет и его — так отвечают на вопрос о типе в точке
     * самого присваивания.
     */
    private TypeSet applyStatements(
      CfgVertex vertex,
      TypeSet incoming,
      @Nullable ParserRuleContext stopAt,
      boolean inclusive
    ) {
      if (!(vertex instanceof BasicBlockVertex block)) {
        return incoming;
      }
      var current = incoming;
      for (var statement : block.statements()) {
        var isStop = statement == stopAt;
        if (isStop && !inclusive) {
          break;
        }
        current = applyStatement(statement, current);
        if (isStop) {
          break;
        }
      }
      return current;
    }

    /**
     * Изменение типа одним оператором: присваивание задаёт тип заново, оператор-мутатор
     * дополняет уже накопленный. Оператор может быть только чем-то одним из двух.
     */
    private TypeSet applyStatement(ParserRuleContext statement, TypeSet incoming) {
      var definition = positionIn(statement, definitionPositions);
      if (definition != null) {
        return assignedTypes.computeIfAbsent(definition, assigned::at);
      }
      var mutation = positionIn(statement, mutationPositions);
      return mutation == null ? incoming : mutations.apply(mutation, incoming);
    }

    /** Позиция из набора, попадающая внутрь оператора, либо {@code null}. */
    @Nullable
    private static Position positionIn(ParserRuleContext statement, Collection<Position> positions) {
      var range = Ranges.create(statement);
      for (var position : positions) {
        if (Ranges.containsPosition(range, position)) {
          return position;
        }
      }
      return null;
    }

    /**
     * Вершины в порядке обхода от входа: так за один проход тип продвигается по прямым
     * рёбрам как можно дальше, и проходов до устойчивого состояния нужно меньше.
     * Недостижимые из входа вершины (мёртвый код) идут в конце.
     */
    private List<CfgVertex> orderedVertices() {
      var ordered = new ArrayList<CfgVertex>(graph.vertexSet().size());
      var visited = new HashSet<CfgVertex>();
      var queue = new ArrayDeque<CfgVertex>();
      queue.add(graph.getEntryPoint());
      visited.add(graph.getEntryPoint());
      while (!queue.isEmpty()) {
        var vertex = queue.poll();
        ordered.add(vertex);
        for (var edge : graph.outgoingEdgesOf(vertex)) {
          var target = graph.getEdgeTarget(edge);
          if (visited.add(target)) {
            queue.add(target);
          }
        }
      }
      for (var vertex : graph.vertexSet()) {
        if (visited.add(vertex)) {
          ordered.add(vertex);
        }
      }
      return ordered;
    }
  }

  /**
   * Все ли присваивания переменной легли в операторы графа. Присваивания, которых в
   * графе нет как отдельных операторов — например связывание переменной в
   * {@code Для Каждого X Из ...}, — расчёт учесть не может: их тип потерялся бы вместе
   * с ними. В таком случае расчёт по потоку неприменим целиком.
   *
   * @param graph               граф тела.
   * @param definitionPositions позиции присваиваний переменной в документе.
   * @return {@code true}, если каждое присваивание внутри тела нашлось в операторах графа.
   */
  private static boolean allPlaced(
    Collection<ParserRuleContext> statements,
    BSLParser.CodeBlockContext body,
    Collection<Position> positions
  ) {
    var bodyRange = Ranges.create(body);
    for (var position : positions) {
      if (Ranges.containsPosition(bodyRange, position) && !isPlaced(statements, position)) {
        return false;
      }
    }
    return true;
  }

  /** Попадает ли какое-нибудь присваивание переменной внутрь самого узла использования. */
  private static boolean coversAnyDefinition(ParserRuleContext use, Collection<Position> definitionPositions) {
    var range = Ranges.create(use);
    for (var position : definitionPositions) {
      if (Ranges.containsPosition(range, position)) {
        return true;
      }
    }
    return false;
  }

  /** Нашлось ли присваивание в каком-нибудь операторе графа. */
  private static boolean isPlaced(Collection<ParserRuleContext> statements, Position position) {
    for (var statement : statements) {
      if (Ranges.containsPosition(Ranges.create(statement), position)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Операторы графа с вершинами, которым они принадлежат. Вид узла оператора граф
   * выбирает сам ({@code AssignmentContext}, {@code CallStatementContext} и другие),
   * поэтому охватывающий оператор ищется по этой карте, а не по классу узла.
   *
   * @param graph граф тела.
   * @return карта «оператор → вершина-блок».
   */
  private static Map<ParserRuleContext, CfgVertex> vertexByStatement(ControlFlowGraph graph) {
    Map<ParserRuleContext, CfgVertex> byStatement = new HashMap<>();
    for (var vertex : graph.vertexSet()) {
      if (vertex instanceof BasicBlockVertex block) {
        for (var statement : block.statements()) {
          byStatement.put(statement, vertex);
        }
      }
    }
    return byStatement;
  }

  /**
   * Тело метода или код модуля, которому принадлежит узел, — единица, по которой
   * строится граф потока управления. Это самый внешний блок кода над узлом:
   * вложенные блоки ветвлений и циклов частями графа не являются.
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
  private static ParserRuleContext enclosingStatement(
    ParserRuleContext node,
    Collection<ParserRuleContext> statements
  ) {
    ParserRuleContext current = node;
    while (current != null) {
      if (statements.contains(current)) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }
}
