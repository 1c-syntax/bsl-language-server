/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 10
)
public class UnreachableCodeDiagnostic extends AbstractVisitorDiagnostic {

  // кэш диапазонов зарегистрированных ошибок
  private List<Range> errorRanges = new ArrayList<>();

  // диапазоны препроцессорных скобок
  private List<Range> preprocessorRanges = new ArrayList<>();

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    errorRanges.clear();
    preprocessorRanges.clear();

    // получим все блоки препроцессора в файле
    List<ParseTree> preprocessors = new ArrayList<>(Trees.findAllRuleNodes(ctx, BSLParser.RULE_preprocessor));

    if (preprocessors.isEmpty()) {
      return super.visitFile(ctx);
    }

    Stack<ParseTree> previousNodes = new Stack<>();
    for (ParseTree node : preprocessors) {

      // если это начало блока, просто закинем его в стэк
      if (((BSLParser.PreprocessorContext) node).preproc_if() != null) {
        previousNodes.add(node);
      } else if ((((BSLParser.PreprocessorContext) node).preproc_else() != null
          || ((BSLParser.PreprocessorContext) node).preproc_elsif() != null)) {

        // для веток условия сначала фиксируем блок который уже прошли
        // затем новую ноду добавим
        savePreprocessorRange(previousNodes, (BSLParser.PreprocessorContext) node);
        previousNodes.add(node);

      } else {
        // в конце условия просто зафиксим прошедший блок
        savePreprocessorRange(previousNodes, (BSLParser.PreprocessorContext) node);
      }
    }

    // сделано для поиска с конца, т.к. у нас есть вложенность, т.е. в большой вложен маленький
    if (preprocessorRanges.size() > 1) {
      Collections.reverse(preprocessorRanges);
    }

    return super.visitFile(ctx);
  }

  private void savePreprocessorRange(Stack<ParseTree> nodes, BSLParser.PreprocessorContext node) {
    if (!nodes.isEmpty()) {
      BSLParser.PreprocessorContext previous = (BSLParser.PreprocessorContext) nodes.pop();
      preprocessorRanges.add(
        RangeHelper.newRange(
          previous.getStop().getLine(),
          previous.getStop().getCharPositionInLine() + previous.getStop().getText().length() + 1,
          node.getStart().getLine(),
          node.getStart().getCharPositionInLine() - 1));
    }
  }

  @Override
  public ParseTree visitContinueStatement(BSLParser.ContinueStatementContext ctx) {
    findAndAddDiagnostic(ctx);
    return super.visitContinueStatement(ctx);
  }

  @Override
  public ParseTree visitReturnStatement(BSLParser.ReturnStatementContext ctx) {
    findAndAddDiagnostic(ctx);
    return super.visitReturnStatement(ctx);
  }

  @Override
  public ParseTree visitGotoStatement(BSLParser.GotoStatementContext ctx) {
    findAndAddDiagnostic(ctx);
    return super.visitGotoStatement(ctx);
  }

  @Override
  public ParseTree visitRaiseStatement(BSLParser.RaiseStatementContext ctx) {
    findAndAddDiagnostic(ctx);
    return super.visitRaiseStatement(ctx);
  }

  @Override
  public ParseTree visitBreakStatement(BSLParser.BreakStatementContext ctx) {
    findAndAddDiagnostic(ctx);
    return super.visitBreakStatement(ctx);
  }

  private void findAndAddDiagnostic(BSLParserRuleContext ctx) {

    // если это вложенный в ранее обработанный блок, то исключим из проверки
    Position pos = new Position(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    for (Range range : errorRanges) {
      if (Ranges.containsPosition(range, pos)) {
        return;
      }
    }

    BSLParserRuleContext nodeParent = (BSLParserRuleContext) ctx.getParent();
    if (nodeParent == null) {
      return;
    }

    BSLParser.StatementContext ppNode = (BSLParser.StatementContext) nodeParent.getParent();
    if (ppNode == null) {
      return;
    }

    BSLParserRuleContext ppNodeParent = (BSLParserRuleContext) ppNode.getParent();
    if (ppNodeParent == null) {
      return;
    }

    List<ParseTree> statements = Trees.findAllRuleNodes(ppNodeParent, BSLParser.RULE_statement)
      .stream()
      .filter(node -> node.getParent().equals(ppNodeParent))
      .collect(Collectors.toList());

    if (statements.size() > 1) {
      Collections.reverse(statements);
    }

    // если в блоке кода есть еще стейты кроме текущего
    if (statements.size() > 1) {

      // найдем последний блок
      BSLParserRuleContext endCurrentBlockNode = getEndCurrentBlockNode(statements, pos);

      // если последний стейт не текущий, значит он будет недостижим
      if (!ppNode.equals(endCurrentBlockNode)) {
        int indexStart = statements.indexOf(ppNode);
        Range newRange = RangeHelper.newRange(
          ((BSLParserRuleContext) statements.get(indexStart - 1)).getStart(),
          endCurrentBlockNode.getStop());
        diagnosticStorage.addDiagnostic(newRange);
        // сохраним в кэш
        errorRanges.add(newRange);
      }
    }
  }

  private BSLParserRuleContext getEndCurrentBlockNode(List<ParseTree> statements, Position pos) {

    // найдем блок препроцессора, в котором лежит наш стейт
    Range preprocRange = null;
    for (Range range : preprocessorRanges) {
      if (Ranges.containsPosition(range, pos)) {
        preprocRange = range;
      }
    }

    // т.к. список реверснут, берем первый элемент
    BSLParserRuleContext endCurrentBlockNode = (BSLParserRuleContext) statements.get(0);

    if (preprocRange != null) {
      // пройдем по всем стейтам (с конца идем) и ищем первый, находящийся в том же блоке
      // препроцессора, что и стейт прерывания
      for (ParseTree statement : statements) {
        Position posStatement = new Position(
          ((BSLParserRuleContext) statement).getStart().getLine(),
          ((BSLParserRuleContext) statement).getStart().getCharPositionInLine());
        if (Ranges.containsPosition(preprocRange, posStatement)) {
          endCurrentBlockNode = (BSLParserRuleContext) statement;
          break;
        }
      }
    }
    return endCurrentBlockNode;
  }
}
