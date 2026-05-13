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
import org.eclipse.lsp4j.Position;

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
    var ast = safeGetAst(documentContext);
    if (ast == null) {
      return Optional.empty();
    }
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(terminal -> terminal.getParent() instanceof org.antlr.v4.runtime.ParserRuleContext prc ? prc : null)
      .map(rule -> Trees.getRootParent(rule, BSLParser.RULE_expression))
      .filter(BSLParser.ExpressionContext.class::isInstance)
      .map(BSLParser.ExpressionContext.class::cast);
  }

  /**
   * Найти RHS присваивания, накрывающего позицию (для случая, когда символ —
   * переменная, и нас интересует выражение, которому её приравняли).
   */
  public static Optional<BSLParser.ExpressionContext> findAssignmentRhs(
    DocumentContext documentContext,
    Position position
  ) {
    var ast = safeGetAst(documentContext);
    if (ast == null) {
      return Optional.empty();
    }
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(terminal -> terminal.getParent() instanceof org.antlr.v4.runtime.ParserRuleContext prc ? prc : null)
      .map(rule -> Trees.getRootParent(rule, BSLParser.RULE_assignment))
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .map(BSLParser.AssignmentContext.class::cast)
      .map(BSLParser.AssignmentContext::expression);
  }

  private static BSLParser.FileContext safeGetAst(DocumentContext documentContext) {
    try {
      return documentContext.getAst();
    } catch (NullPointerException e) {
      // tokenizer ещё не инициализирован (документ не открыт/не прочитан) —
      // вывод типов корректно ничего не возвращает.
      return null;
    }
  }

  /**
   * Найти RHS-выражения всех присваиваний переменной с заданным именем в
   * пределах AST документа (LHS — простой идентификатор, без чейна).
   * <p>
   * Используется как замена обходу {@link com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex}
   * при выводе типов переменной — индекс может быть не наполнен (например,
   * до первого rebuild'а документа), а нам нужны все позиции присваивания
   * для union'а типов.
   */
  public static java.util.List<BSLParser.ExpressionContext> findAssignmentRhsForVariable(
    DocumentContext documentContext,
    String variableName
  ) {
    var ast = safeGetAst(documentContext);
    if (ast == null) {
      return java.util.List.of();
    }
    var result = new java.util.ArrayList<BSLParser.ExpressionContext>();
    collectAssignments(ast, variableName, result);
    return result;
  }

  private static void collectAssignments(
    org.antlr.v4.runtime.tree.ParseTree node,
    String variableName,
    java.util.List<BSLParser.ExpressionContext> sink
  ) {
    if (node instanceof BSLParser.AssignmentContext assignment) {
      var lhs = assignment.lValue();
      if (lhs != null && lhs.acceptor() == null && lhs.IDENTIFIER() != null
        && lhs.IDENTIFIER().getText().equalsIgnoreCase(variableName)) {
        var rhs = assignment.expression();
        if (rhs != null) {
          sink.add(rhs);
        }
      }
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      collectAssignments(node.getChild(i), variableName, sink);
    }
  }


  public static Optional<BslExpression> findExpressionTree(
    DocumentContext documentContext,
    Position position
  ) {
    return findExpressionContext(documentContext, position)
      .map(ExpressionTreeBuildingVisitor::buildExpressionTree);
  }
}
