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
    boolean invoke(AbstractCfgVisitor target, CfgVertex vertex);
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
  protected boolean visitBasicBlock(BasicBlockVertex v) {
    return true;
  }

  /**
   * Метод рекомендуется переопределять, если необходимо одним методом
   * обрабатывать любые типы ветвлений. Вызвать super, если надо диспетчеризовать визитор
   * к более специальным листовым классам ветвлений
   *
   * @param v посещаемая ветка
   */
  protected boolean visitBranchingVertex(BranchingVertex v) {
    dispatchVertex(v);
    return false;
  }

  /**
   * Метод рекомендуется переопределять, если необходимо одним методом
   * обрабатывать любые типы циклов. Вызвать super, если надо диспетчеризовать визитор
   * к более специальным листовым классам циклов
   *
   * @param v посещаемая ветка
   */
  protected boolean visitLoopVertex(LoopVertex v) {
    dispatchVertex(v);
    return false;
  }

  /**
   * @param v обход условной ветки
   */
  protected boolean visitConditionalVertex(ConditionalVertex v) {
    return true;
  }

  /**
   * @param v обход цикла while
   */
  protected boolean visitWhileLoopVertex(WhileLoopVertex v) {
    return true;
  }

  /**
   * @param v обход цикла for
   */
  protected boolean visitForLoopVertex(ForLoopVertex v) {
    return true;
  }

  /**
   * @param v обход цикла forEach
   */
  protected boolean visitForeachLoopVertex(ForeachLoopVertex v) {
    return true;
  }

  /**
   * @param v обход узла метки
   */
  protected boolean visitLabelVertex(LabelVertex v) {
    return true;
  }

  /**
   * @param v обход узла метки
   */
  protected boolean visitTryExceptVertex(TryExceptVertex v) {
    return true;
  }

  /**
   * @param v обход узла метки
   */
  protected boolean visitExitVertex(ExitVertex v) {
    return false;
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

    var shouldWalkRoutes = dispatch.invoke(this, v);
    if (shouldWalkRoutes) {
      dispatchRoutes(v);
    }
  }

  private void visitSuperclassingVertexType(BranchingVertex v) {
    if (v instanceof LoopVertex && visitLoopVertex((LoopVertex) v)) {
      dispatchRoutes(v);
    } else {
      if (visitBranchingVertex(v)) {
        dispatchRoutes(v);
      }
    }
  }

}
