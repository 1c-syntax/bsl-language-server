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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Утилита: находит ближайшее «полное» выражение в AST документа, накрывающее
 * заданную {@link Position}, и строит для него {@link BslExpression}.
 * <p>
 * Используется для случаев, когда нам нужно вывести тип значения по
 * курсору/референсу: assignment RHS (например, для variable definition),
 * условие тернарного оператора, аргумент вызова и т.п.
 */
@UtilityClass
public class ExpressionAtPosition {

  /**
   * Найти выражение по позиции в документе.
   *
   * @param documentContext контекст документа (источник AST)
   * @param position позиция, для которой ищется expression
   * @return ExpressionContext, накрывающий позицию (как правило — RHS присваивания)
   */
  public static Optional<BSLParser.ExpressionContext> findExpressionContext(
    DocumentContext documentContext,
    Position position
  ) {
    return findEnclosingRule(documentContext, position, BSLParser.RULE_expression);
  }

  /**
   * Найти complexIdentifier-предка terminal'а под курсором — для случая, когда
   * выражение стоит вне expression-узла (например, callStatement
   * {@code Объект.Метод();} в роли отдельного утверждения).
   */
  public static Optional<BSLParser.ComplexIdentifierContext> findComplexIdentifierContext(
    DocumentContext documentContext,
    Position position
  ) {
    return findEnclosingRule(documentContext, position, BSLParser.RULE_complexIdentifier);
  }

  /**
   * Найти callStatement-предка terminal'а под курсором — для случая
   * standalone-вызова {@code Объект.Метод();}: в BSL grammar callStatement
   * не оборачивается в complexIdentifier.
   */
  public static Optional<BSLParser.CallStatementContext> findCallStatementContext(
    DocumentContext documentContext,
    Position position
  ) {
    return findEnclosingRule(documentContext, position, BSLParser.RULE_callStatement);
  }

  /**
   * Найти lValue-предка terminal'а под курсором — для случая dereference на левой
   * части присваивания ({@code Объект.Свойство = …}). lValue — отдельная продукция
   * грамматики и не накрывается ни expression, ни complexIdentifier, поэтому
   * без отдельного lookup'а hover/memberAt ничего не находят на lValue-цепочке.
   */
  public static Optional<BSLParser.LValueContext> findLValueContext(
    DocumentContext documentContext,
    Position position
  ) {
    return findEnclosingRule(documentContext, position, BSLParser.RULE_lValue);
  }

  /**
   * Найти RHS присваивания, накрывающего позицию (для случая, когда символ —
   * переменная, и нас интересует выражение, которому её приравняли).
   */
  public static Optional<BSLParser.ExpressionContext> findAssignmentRhs(
    DocumentContext documentContext,
    Position position
  ) {
    return findAssignment(documentContext, position).map(BSLParser.AssignmentContext::expression);
  }

  /**
   * Найти AssignmentContext, накрывающий позицию (для извлечения trailing-комментария
   * со строки присваивания).
   */
  public static Optional<BSLParser.AssignmentContext> findAssignment(
    DocumentContext documentContext,
    Position position
  ) {
    var ast = safeGetAst(documentContext);
    if (ast == null) {
      return Optional.empty();
    }
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(terminal -> terminal.getParent() instanceof ParserRuleContext prc ? prc : null)
      .map(rule -> Trees.getRootParent(rule, BSLParser.RULE_assignment))
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .map(BSLParser.AssignmentContext.class::cast);
  }

  /**
   * Найти ForEachStatementContext, в котором IDENTIFIER (итерационная
   * переменная) расположен в указанной позиции. Используется, чтобы по
   * позиции декларации переменной для-каждого получить выражение коллекции
   * и далее её типы элементов.
   */
  public static Optional<BSLParser.ForEachStatementContext> findForEachBindingAt(
    DocumentContext documentContext,
    Position position
  ) {
    var ast = safeGetAst(documentContext);
    if (ast == null) {
      return Optional.empty();
    }
    var hit = Trees.findTerminalNodeContainsPosition(ast, position).orElse(null);
    if (hit == null) {
      return Optional.empty();
    }
    var parent = hit.getParent();
    if (!(parent instanceof BSLParser.ForEachStatementContext forEach)) {
      return Optional.empty();
    }
    return forEach.IDENTIFIER() == hit ? Optional.of(forEach) : Optional.empty();
  }

  /**
   * Найти узел правила {@code ruleIndex}, накрывающий терминал под курсором. Берём
   * терминал в позиции, поднимаемся к его непосредственному родителю и ищем целевое
   * правило, считая сам родитель кандидатом (см. {@link #ancestorOrSelf}). Тип
   * результата выводится из ожидаемого {@code Optional<T>} вызывающего метода.
   */
  private static <T extends ParserRuleContext> Optional<T> findEnclosingRule(
    DocumentContext documentContext, Position position, int ruleIndex
  ) {
    var ast = safeGetAst(documentContext);
    if (ast == null) {
      return Optional.empty();
    }
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(terminal -> terminal.getParent() instanceof ParserRuleContext prc ? prc : null)
      .map(rule -> ExpressionAtPosition.<T>ancestorOrSelf(rule, ruleIndex));
  }

  /**
   * Найти ближайший узел правила {@code ruleIndex}, считая сам {@code node}
   * кандидатом (inclusive). {@link Trees#getAncestorByRuleIndex} ищет строго среди
   * предков и пропускает сам узел — но в текущей грамматике ресивер-идентификатор
   * стоит прямым ребёнком целевого правила (например,
   * {@code (callStatement Идентификатор (incompleteAccess .))} для висячей точки
   * {@code Идентификатор.}), поэтому без проверки самого узла lookup даёт null.
   */
  @Nullable
  private static <T extends ParserRuleContext> T ancestorOrSelf(ParserRuleContext node, int ruleIndex) {
    if (node.getRuleIndex() == ruleIndex) {
      @SuppressWarnings("unchecked")
      var self = (T) node;
      return self;
    }
    return Trees.getAncestorByRuleIndex(node, ruleIndex);
  }

  private static BSLParser.@Nullable FileContext safeGetAst(DocumentContext documentContext) {
    try {
      return documentContext.getAst();
    } catch (NullPointerException e) {
      // tokenizer ещё не инициализирован (документ не открыт/не прочитан) —
      // вывод типов корректно ничего не возвращает.
      return null;
    }
  }

  /**
   * Удобный shortcut: построить {@link BslExpression} для выражения по позиции.
   */
  public static Optional<BslExpression> findExpressionTree(
    DocumentContext documentContext,
    Position position
  ) {
    var exprCtx = findExpressionContext(documentContext, position);
    if (exprCtx.isPresent()) {
      return exprCtx.map(ExpressionTreeBuildingVisitor::buildExpressionTree);
    }
    var complexIdent = findComplexIdentifierContext(documentContext, position);
    if (complexIdent.isPresent()) {
      return complexIdent.map(ExpressionTreeBuildingVisitor::buildExpressionTree);
    }
    var callStmt = findCallStatementContext(documentContext, position);
    if (callStmt.isPresent()) {
      return callStmt.map(ExpressionTreeBuildingVisitor::buildExpressionTree);
    }
    var lValue = findLValueContext(documentContext, position);
    if (lValue.isPresent()) {
      return lValue.map(ExpressionTreeBuildingVisitor::buildExpressionTree);
    }
    return Optional.empty();
  }
}
