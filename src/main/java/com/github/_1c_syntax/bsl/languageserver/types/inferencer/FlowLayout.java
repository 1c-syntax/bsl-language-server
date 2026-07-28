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
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Разложенное по позициям тело метода: операторы графа с их границами и вершинами,
 * плюс порядок обхода вершин.
 * <p>
 * Считается один раз на тело и переиспользуется всеми запросами по всем переменным.
 * Границы операторов хранятся числами, а не объектами диапазонов: поиск оператора по
 * позиции идёт на каждый запрос типа, и создание диапазона на каждый оператор
 * обходилось дороже самого поиска.
 */
final class FlowLayout {

  /**
   * Оператор с границами и вершиной, которой он принадлежит.
   */
  private record Slot(
    ParserRuleContext statement,
    int startLine,
    int startChar,
    int endLine,
    int endChar,
    CfgVertex vertex
  ) {

    /** Лежит ли позиция внутри границ оператора. */
    boolean covers(Position position) {
      var line = position.getLine();
      if (line < startLine || line > endLine) {
        return false;
      }
      if (line == startLine && position.getCharacter() < startChar) {
        return false;
      }
      return line != endLine || position.getCharacter() <= endChar;
    }
  }

  private final List<Slot> slots;
  private final Map<ParserRuleContext, Slot> byStatement;
  private final List<CfgVertex> orderedVertices;
  private final int bodyStartLine;
  private final int bodyEndLine;

  private FlowLayout(
    List<Slot> slots,
    Map<ParserRuleContext, Slot> byStatement,
    List<CfgVertex> orderedVertices,
    int bodyStartLine,
    int bodyEndLine
  ) {
    this.slots = slots;
    this.byStatement = byStatement;
    this.orderedVertices = orderedVertices;
    this.bodyStartLine = bodyStartLine;
    this.bodyEndLine = bodyEndLine;
  }

  /**
   * Разложить тело по графу его потока управления.
   *
   * @param graph граф тела.
   * @param body  блок кода тела.
   * @return раскладка.
   */
  static FlowLayout of(ControlFlowGraph graph, BSLParser.CodeBlockContext body) {
    var slots = new ArrayList<Slot>();
    Map<ParserRuleContext, Slot> byStatement = new IdentityHashMap<>();
    for (var vertex : graph.vertexSet()) {
      if (!(vertex instanceof BasicBlockVertex block)) {
        continue;
      }
      for (var statement : block.statements()) {
        var start = statement.getStart();
        var stop = statement.getStop() == null ? start : statement.getStop();
        var slot = new Slot(
          statement,
          start.getLine() - 1,
          start.getCharPositionInLine(),
          stop.getLine() - 1,
          stop.getCharPositionInLine() + stop.getText().length(),
          vertex
        );
        slots.add(slot);
        byStatement.put(statement, slot);
      }
    }
    var bodyStart = body.getStart();
    var bodyStop = body.getStop() == null ? bodyStart : body.getStop();
    return new FlowLayout(
      List.copyOf(slots),
      byStatement,
      orderVertices(graph),
      bodyStart.getLine() - 1,
      bodyStop.getLine() - 1
    );
  }

  /**
   * Вершины в порядке обхода от входа: так за один проход расчёта тип продвигается по
   * прямым рёбрам как можно дальше. Недостижимые из входа вершины (мёртвый код) — в конце.
   */
  private static List<CfgVertex> orderVertices(ControlFlowGraph graph) {
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
    return List.copyOf(ordered);
  }

  /** Вершины в порядке обхода от входа. */
  List<CfgVertex> orderedVertices() {
    return orderedVertices;
  }

  /** Операторы вершины по порядку следования в коде. */
  List<ParserRuleContext> statementsOf(CfgVertex vertex) {
    return vertex instanceof BasicBlockVertex block ? block.statements() : List.of();
  }

  /** Вершина, которой принадлежит оператор; {@code null}, если оператора в графе нет. */
  @Nullable
  CfgVertex vertexOf(ParserRuleContext statement) {
    var slot = byStatement.get(statement);
    return slot == null ? null : slot.vertex();
  }

  /** Есть ли оператор в графе. */
  boolean hasStatement(ParserRuleContext statement) {
    return byStatement.containsKey(statement);
  }

  /** Оператор, накрывающий позицию; {@code null}, если такого нет. */
  @Nullable
  ParserRuleContext statementAt(Position position) {
    for (var slot : slots) {
      if (slot.covers(position)) {
        return slot.statement();
      }
    }
    return null;
  }

  /** Накрывает ли позицию хоть один оператор графа. */
  boolean isPlaced(Position position) {
    return statementAt(position) != null;
  }

  /** Лежит ли позиция внутри тела (по строкам). */
  boolean insideBody(Position position) {
    return position.getLine() >= bodyStartLine && position.getLine() <= bodyEndLine;
  }

  /**
   * Позиции из набора, попавшие в каждый оператор. Считается один раз на набор позиций
   * и переиспользуется всеми проходами расчёта.
   *
   * @param positions позиции присваиваний или операторов-мутаторов.
   * @return карта «оператор → позиция из набора»; операторов без попаданий в ней нет.
   */
  Map<ParserRuleContext, Position> index(Iterable<Position> positions) {
    Map<ParserRuleContext, Position> byStatementPosition = new HashMap<>();
    for (var position : positions) {
      var statement = statementAt(position);
      if (statement != null) {
        byStatementPosition.put(statement, position);
      }
    }
    return byStatementPosition;
  }
}
