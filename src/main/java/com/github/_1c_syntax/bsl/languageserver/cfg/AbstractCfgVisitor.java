/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Абстрактный класс обхода графа управления. Переопределяйте методы-визиторы для обхода
 * конкретных узлов графа управления
 */
public abstract class AbstractCfgVisitor {

  @FunctionalInterface
  private interface VertexVisitor {
    void invoke(AbstractCfgVisitor target, CfgVertex vertex);
  }

  @FunctionalInterface
  private interface EdgeVisitor {
    boolean invoke(AbstractCfgVisitor target, CfgEdge edge);
  }

  private static final Map<Class<? extends CfgVertex>, VertexVisitor> dispatchVertexFunctions
    = createVertexDispatch();
  private static final Map<CfgEdgeType, EdgeVisitor> dispatchEdgeFunctions
    = createEdgeDispatch();

  private static Map<Class<? extends CfgVertex>, VertexVisitor> createVertexDispatch() {
    Map<Class<? extends CfgVertex>, VertexVisitor> map = new HashMap<>();
    map.put(BasicBlockVertex.class, (t, v) -> t.visitBasicBlock((BasicBlockVertex) v));
    map.put(ConditionalVertex.class, (t, v) -> t.visitConditionalVertex((ConditionalVertex) v));
    map.put(WhileLoopVertex.class, (t, v) -> t.visitWhileLoopVertex((WhileLoopVertex) v));
    map.put(ForLoopVertex.class, (t, v) -> t.visitForLoopVertex((ForLoopVertex) v));
    map.put(ForeachLoopVertex.class, (t, v) -> t.visitForeachLoopVertex((ForeachLoopVertex) v));
    map.put(TryExceptVertex.class, (t, v) -> t.visitTryExceptVertex((TryExceptVertex) v));
    map.put(LabelVertex.class, (t, v) -> t.visitLabelVertex((LabelVertex) v));
    map.put(ExitVertex.class, (t, v) -> t.visitExitVertex((ExitVertex) v));

    return map;
  }

  private static Map<CfgEdgeType, EdgeVisitor> createEdgeDispatch() {
    var map = new EnumMap<CfgEdgeType, EdgeVisitor>(CfgEdgeType.class);
    map.put(CfgEdgeType.DIRECT, AbstractCfgVisitor::visitDirectEdge);
    map.put(CfgEdgeType.TRUE_BRANCH, AbstractCfgVisitor::visitTrueEdge);
    map.put(CfgEdgeType.FALSE_BRANCH, AbstractCfgVisitor::visitFalseEdge);
    map.put(CfgEdgeType.LOOP_ITERATION, AbstractCfgVisitor::visitLoopIterationEdge);
    return map;
  }

  protected final ControlFlowGraph graph;

  protected AbstractCfgVisitor(ControlFlowGraph graph) {
    this.graph = graph;
  }

  /**
   * @param v начинает обход с указанной вершины
   */
  public void visitVertex(CfgVertex v) {
    if (v instanceof BranchingVertex) {
      visitSuperclassingVertexType((BranchingVertex) v);
      return;
    }
    dispatchVertex(v);
  }

  /**
   * @param v вершина линейного блока.
   */
  protected void visitBasicBlock(BasicBlockVertex v) {
    dispatchRoutes(v);
  }

  /**
   * Метод рекомендуется переопределять, если необходимо одним методом
   * обрабатывать любые типы ветвлений. Вызвать super, если надо диспетчеризовать визитор
   * к более специальным листовым классам ветвлений
   *
   * @param v посещаемая ветка
   */
  protected void visitBranchingVertex(BranchingVertex v) {
    dispatchVertex(v);
  }

  /**
   * Метод рекомендуется переопределять, если необходимо одним методом
   * обрабатывать любые типы циклов. Вызвать super, если надо диспетчеризовать визитор
   * к более специальным листовым классам циклов
   *
   * @param v посещаемая ветка
   */
  protected void visitLoopVertex(LoopVertex v) {
    dispatchVertex(v);
  }

  /**
   * @param v обход условной ветки
   */
  protected void visitConditionalVertex(ConditionalVertex v) {
    dispatchRoutes(v);
  }

  /**
   * @param v обход цикла while
   */
  protected void visitWhileLoopVertex(WhileLoopVertex v) {
    dispatchRoutes(v);
  }

  /**
   * @param v обход цикла for
   */
  protected void visitForLoopVertex(ForLoopVertex v) {
    dispatchRoutes(v);
  }

  /**
   * @param v обход цикла forEach
   */
  protected void visitForeachLoopVertex(ForeachLoopVertex v) {
    dispatchRoutes(v);
  }

  /**
   * @param v обход узла метки
   */
  protected void visitLabelVertex(LabelVertex v) {
    dispatchRoutes(v);
  }

  /**
   * @param v обход узла метки
   */
  protected void visitTryExceptVertex(TryExceptVertex v) {
    dispatchRoutes(v);
  }

  /**
   * @param v обход узла метки
   */
  protected void visitExitVertex(ExitVertex v) {
    dispatchRoutes(v);
  }

  /**
   * @param e ребро перехода по потоку управления
   * @return true - если переход нужно совершить.
   * false - если обход в этом направлении не нужен
   */
  protected boolean visitDirectEdge(CfgEdge e) {
    return true;
  }

  /**
   * @param e ребро перехода по потоку управления
   * @return true - если переход нужно совершить.
   * false - если обход в этом направлении не нужен
   */
  protected boolean visitTrueEdge(CfgEdge e) {
    return true;
  }

  /**
   * @param e ребро перехода по потоку управления
   * @return true - если переход нужно совершить.
   * false - если обход в этом направлении не нужен
   */
  protected boolean visitFalseEdge(CfgEdge e) {
    return true;
  }

  /**
   * @param e ребро перехода по потоку управления
   * @return true - если переход нужно совершить.
   * false - если обход в этом направлении не нужен
   */
  protected boolean visitLoopIterationEdge(CfgEdge e) {
    // чаще всего проходить назад/наверх по циклу в КФГ не нужно. Кому понадобится - переопределит
    return false;
  }

  private void dispatchRoutes(CfgVertex v) {
    var routes = graph.outgoingEdgesOf(v);
    for (var route : routes) {
      var router = dispatchEdgeFunctions.get(route.getType());
      var shouldFollow = router.invoke(this, route);
      if (shouldFollow) {
        var target = graph.getEdgeTarget(route);
        visitVertex(target);
      }
    }
  }

  /**
   * @param v возможность вызвать прямую диспетчеризацию вершины из подкласса
   */
  protected final void dispatchVertex(CfgVertex v) {
    var dispatch = dispatchVertexFunctions.get(v.getClass());
    if (dispatch == null) {
      throw new IllegalStateException();
    }

    dispatch.invoke(this, v);
  }

  private void visitSuperclassingVertexType(BranchingVertex v) {
    if (v instanceof LoopVertex) {
      visitLoopVertex((LoopVertex) v);
    } else {
      visitBranchingVertex(v);
    }
  }
}
