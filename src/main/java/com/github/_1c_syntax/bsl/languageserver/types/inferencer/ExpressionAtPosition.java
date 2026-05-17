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
   * Найти complexIdentifier-предка terminal'а под курсором — для случая, когда
   * выражение стоит вне expression-узла (например, callStatement
   * {@code Объект.Метод();} в роли отдельного утверждения).
   */
  public static Optional<BSLParser.ComplexIdentifierContext> findComplexIdentifierContext(
    DocumentContext documentContext,
    Position position
  ) {
    var ast = safeGetAst(documentContext);
    if (ast == null) {
      return Optional.empty();
    }
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(terminal -> terminal.getParent() instanceof org.antlr.v4.runtime.ParserRuleContext prc ? prc : null)
      .map(rule -> Trees.getRootParent(rule, BSLParser.RULE_complexIdentifier))
      .filter(BSLParser.ComplexIdentifierContext.class::isInstance)
      .map(BSLParser.ComplexIdentifierContext.class::cast);
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
    var ast = safeGetAst(documentContext);
    if (ast == null) {
      return Optional.empty();
    }
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(terminal -> terminal.getParent() instanceof org.antlr.v4.runtime.ParserRuleContext prc ? prc : null)
      .map(rule -> Trees.getRootParent(rule, BSLParser.RULE_callStatement))
      .filter(BSLParser.CallStatementContext.class::isInstance)
      .map(BSLParser.CallStatementContext.class::cast);
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
      .map(terminal -> terminal.getParent() instanceof org.antlr.v4.runtime.ParserRuleContext prc ? prc : null)
      .map(rule -> Trees.getRootParent(rule, BSLParser.RULE_assignment))
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .map(BSLParser.AssignmentContext.class::cast);
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
    return findCallStatementContext(documentContext, position)
      .map(ExpressionTreeBuildingVisitor::buildExpressionTree);
  }
}
