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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Тип переменной в конкретной точке кода — расчёт по графу потока управления метода.
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
 * Расчёт ведётся <b>по одной переменной</b>: остальные операторы для неё прозрачны.
 * <p>
 * Про присваиваемые типы, изменения мутаторов и сужение по условиям анализатор не знает
 * ничего — их отдают колбэки {@link FlowInputs}, а места изменений передаются готовыми
 * списками позиций. Поэтому здесь нет ни вывода типов выражений, ни индекса ссылок.
 * <p>
 * Кэшируется двоякое, и оба кэша сбрасываются по жизненному циклу документа: разложенное
 * по позициям тело (одно на тело, общее для всех переменных) и рассчитанное окружение по
 * вершинам (одно на переменную). Без второго неподвижная точка считалась бы заново на
 * каждый запрос типа, а диагностики спрашивают тип на каждое обращение к члену.
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
  private final Map<URI, Map<Object, Facts>> factsByUri = new ConcurrentHashMap<>();

  /**
   * Рассчитанное по переменной, что не зависит от точки запроса: окружение по вершинам и
   * разложенные по операторам места изменений типа. Считается один раз, дальше запрос
   * лишь доигрывает операторы своего блока.
   *
   * @param beforeStatement       тип переменной перед каждым оператором. Хранится по
   *                              операторам, а не по вершинам: иначе запрос доигрывал бы
   *                              операторы блока от его начала до места использования, а
   *                              это на каждое обращение к члену в длинном методе.
   * @param definitionByStatement присваивания по операторам, в которых они стоят.
   * @param mutationByStatement   операторы-мутаторы по операторам, в которых они стоят.
   */
  private record Facts(
    boolean applicable,
    Map<ParserRuleContext, TypeSet> beforeStatement,
    Map<ParserRuleContext, Position> definitionByStatement,
    Map<ParserRuleContext, Position> mutationByStatement
  ) {

    /**
     * Расчёт по потоку к этой переменной неприменим: какое-то изменение типа не легло в
     * граф отдельным оператором, и его вклад потерялся бы. Ответ запоминается наравне с
     * рассчитанным — проверять применимость на каждое обращение незачем.
     */
    private static final Facts NOT_APPLICABLE = new Facts(false, Map.of(), Map.of(), Map.of());
  }

  /**
   * Присваиваемые переменной типы в точке присваивания.
   */
  @FunctionalInterface
  public interface AssignedTypes {

    /**
     * Типы, которые переменная получает в этом присваивании.
     *
     * @param statement оператор графа, в котором стоит присваивание. Передаётся, чтобы
     *                  вызывающему не пришлось искать его спуском по дереву разбора:
     *                  расчёт уже знает этот узел.
     * @param position  позиция присваивания — начало имени переменной в левой части.
     * @return присваиваемые типы; пустой набор, если вывести их не удалось.
     */
    TypeSet at(ParserRuleContext statement, Position position);
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
   * @param cacheKey            ключ кэша рассчитанного окружения — сама переменная.
   * @param cacheable           можно ли запоминать результат: вложенный расчёт мог быть
   *                            усечён защитой от циклов, такой результат переиспользовать нельзя.
   * @param entryFact           тип переменной на входе в тело: объявленные типы параметра,
   *                            типы из аннотаций и прочее, что известно до первого присваивания.
   * @param definitionPositions позиции всех присваиваний переменной в документе;
   *                            учитываются только попавшие в анализируемое тело.
   * @param mutationPositions   позиции операторов, меняющих тип переменной на месте.
   * @param assigned            колбэк, отдающий присваиваемые типы по позиции.
   * @param mutations           колбэк, применяющий изменение оператора-мутатора.
   * @param narrowing           колбэк, сужающий тип по охраняющему условию ветки.
   *     <p>
   *     Первые три отдаются поставщиками, а не значениями: тип на входе в тело тянет разбор
   *     документирующего комментария, а места изменений — обход индекса ссылок. При готовом
   *     ответе в кэше ничего этого не нужно, а спрашивают тип на каждое обращение к переменной.
   */
  public record FlowInputs(
    Object cacheKey,
    boolean cacheable,
    Supplier<TypeSet> entryFact,
    Supplier<Collection<Position>> definitionPositions,
    Supplier<Collection<Position>> mutationPositions,
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
    var layout = layoutOf(documentContext, body);
    var useStatement = enclosingStatement(use, layout);
    if (useStatement == null) {
      return null;
    }
    return typeAtStatement(documentContext, body, layout, useStatement, use, false, inputs);
  }

  /**
   * Тип переменной в позиции документа — для вызывающих, у которых узла нет, а есть
   * только позиция (ссылка из индекса). Тело метода ищется перебором объявлений верхнего
   * уровня, оператор — по разложенному телу: спуска по дереву разбора тут нет.
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
    var layout = layoutOf(documentContext, body);
    var useStatement = layout.statementAt(position);
    if (useStatement == null) {
      return null;
    }
    return typeAtStatement(documentContext, body, layout, useStatement, null, atDefinition, inputs);
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

  /** Общая часть обоих входов: проверки применимости и сам расчёт. */
  @Nullable
  private TypeSet typeAtStatement(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    ParserRuleContext useStatement,
    @Nullable ParserRuleContext useNode,
    boolean atDefinition,
    FlowInputs inputs
  ) {
    if (layout.vertexOf(useStatement) == null) {
      return null;
    }
    var facts = factsOf(documentContext, body, layout, inputs);
    if (!facts.applicable()) {
      return null;
    }
    var before = facts.beforeStatement().getOrDefault(useStatement, TypeSet.EMPTY);
    var inclusive = useNode == null
      ? atDefinition
      : coversDefinition(useNode, facts.definitionByStatement().get(useStatement));
    if (!inclusive) {
      return before;
    }
    // Использование стоит на самом присваивании — нужен тип после него.
    var definition = facts.definitionByStatement().get(useStatement);
    if (definition != null) {
      return inputs.assigned().at(useStatement, definition);
    }
    var mutation = facts.mutationByStatement().get(useStatement);
    return mutation == null ? before : inputs.mutations().apply(mutation, before);
  }

  /**
   * Рассчитанное окружение по вершинам — из кэша либо посчитанное на месте. Окружение не
   * зависит от точки запроса, поэтому одного расчёта хватает на все обращения к переменной.
   */
  private Facts factsOf(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    FlowInputs inputs
  ) {
    var byVariable = inputs.cacheable()
      ? factsByUri.computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>())
      : null;
    if (byVariable != null) {
      var cached = byVariable.get(inputs.cacheKey());
      if (cached != null) {
        return cached;
      }
    }
    // Расчёт идёт ВНЕ computeIfAbsent: он не мгновенный, и под замком корзины на нём
    // выстраивались бы все потоки пакетного анализа. Вдобавок расчёт одной переменной
    // тянет вывод типов, который может попросить тип другой переменной того же
    // документа, — это рекурсивное обновление той же карты, что запрещено.
    var definitions = inputs.definitionPositions().get();
    var mutations = inputs.mutationPositions().get();
    Facts computed;
    // Присваиваний нет — расчёту нечего перекрывать; а если хоть одно изменение типа не
    // легло в граф отдельным оператором, его вклад потерялся бы. И там, и там точнее
    // прежний путь с обходом всей области видимости.
    if (definitions.isEmpty() || !allPlaced(layout, definitions) || !allPlaced(layout, mutations)) {
      computed = Facts.NOT_APPLICABLE;
    } else {
      var definitionByStatement = layout.index(definitions);
      var mutationByStatement = layout.index(mutations);
      var graph = controlFlowGraphIndex.graphOf(documentContext, body, CfgBuildOptions.defaults());
      var pass = new Pass(graph, layout, inputs, definitionByStatement, mutationByStatement);
      computed = new Facts(true, pass.computeStatementFacts(), definitionByStatement, mutationByStatement);
    }
    if (byVariable == null) {
      return computed;
    }
    var previous = byVariable.putIfAbsent(inputs.cacheKey(), computed);
    return previous == null ? computed : previous;
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
   * Один расчёт по одной переменной: держит исходные данные и разложенное тело, чтобы не
   * таскать их через все шаги.
   */
  private static final class Pass {

    private final ControlFlowGraph graph;
    private final FlowLayout layout;
    private final FlowInputs inputs;
    private final Map<ParserRuleContext, Position> definitionByStatement;
    private final Map<ParserRuleContext, Position> mutationByStatement;

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
      Map<ParserRuleContext, Position> definitionByStatement,
      Map<ParserRuleContext, Position> mutationByStatement
    ) {
      this.graph = graph;
      this.layout = layout;
      this.inputs = inputs;
      this.definitionByStatement = definitionByStatement;
      this.mutationByStatement = mutationByStatement;
    }

    /**
     * Тип переменной перед каждым оператором тела. Считается один раз: сперва окружение
     * по вершинам до неподвижной точки, затем один проход по операторам каждой вершины.
     *
     * @return тип перед каждым оператором графа.
     */
    private Map<ParserRuleContext, TypeSet> computeStatementFacts() {
      var byVertex = computeEntryFacts();
      Map<ParserRuleContext, TypeSet> beforeStatement = new IdentityHashMap<>();
      for (var vertex : layout.orderedVertices()) {
        var current = byVertex.getOrDefault(vertex, TypeSet.EMPTY);
        for (var statement : layout.statementsOf(vertex)) {
          beforeStatement.put(statement, current);
          current = applyStatement(statement, current);
        }
      }
      return beforeStatement;
    }

    /**
     * Тип переменной на входе в каждую вершину. Расчёт идёт проходами по всем вершинам,
     * пока значения не перестанут меняться: так тип, приходящий по обратному ребру
     * цикла, доезжает до вершин, посещённых раньше него.
     */
    private Map<CfgVertex, TypeSet> computeEntryFacts() {
      Map<CfgVertex, TypeSet> facts = new IdentityHashMap<>();
      facts.put(graph.getEntryPoint(), inputs.entryFact().get());

      for (var pass = 0; pass < MAX_PASSES; pass++) {
        var changed = false;
        for (var vertex : layout.orderedVertices()) {
          var incoming = joinOfPredecessors(vertex, facts);
          if (vertex == graph.getEntryPoint()) {
            incoming = incoming.union(inputs.entryFact().get());
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
     * Объединение типов на выходе всех предшественников вершины. По рёбрам веток условия
     * тип проходит суженным: на истинной ветке верно само условие, на ложной — его
     * отрицание. Этим же получается сужение охранным предложением
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
      if (!(predecessor instanceof ConditionalVertex conditional)
        || edgeType != CfgEdgeType.TRUE_BRANCH && edgeType != CfgEdgeType.FALSE_BRANCH) {
        return outgoing;
      }
      return inputs.narrowing()
        .narrow(conditional.getExpression(), edgeType == CfgEdgeType.TRUE_BRANCH, outgoing);
    }

    /**
     * Применить к типу операторы вершины по порядку. Если задан {@code stopAt}, обход
     * останавливается на этом операторе: {@code X = F(X)} читает прежний тип, поэтому по
     * умолчанию оператор, на котором остановились, не применяется. Флаг {@code inclusive}
     * применяет и его — так отвечают на вопрос о типе в точке самого присваивания.
     */
    private TypeSet applyStatements(
      CfgVertex vertex,
      TypeSet incoming,
      @Nullable ParserRuleContext stopAt,
      boolean inclusive
    ) {
      var current = incoming;
      for (var statement : layout.statementsOf(vertex)) {
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
      var definition = definitionByStatement.get(statement);
      if (definition != null) {
        return assignedTypes.computeIfAbsent(definition, position -> inputs.assigned().at(statement, position));
      }
      var mutation = mutationByStatement.get(statement);
      return mutation == null ? incoming : inputs.mutations().apply(mutation, incoming);
    }
  }

  /**
   * Стоит ли использование на самом присваивании: тогда спрашивают тип, который переменная
   * получает здесь, а не тот, что был до неё.
   *
   * @param use        узел использования.
   * @param definition позиция присваивания в этом же операторе; {@code null} — присваивания нет.
   * @return {@code true}, если присваивание попадает внутрь узла использования.
   */
  private static boolean coversDefinition(ParserRuleContext use, @Nullable Position definition) {
    return definition != null && Ranges.containsPosition(Ranges.create(use), definition);
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
