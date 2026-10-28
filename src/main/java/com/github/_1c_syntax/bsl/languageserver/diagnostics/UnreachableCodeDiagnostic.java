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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.cfg.ExitVertex;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorReachability;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.DESIGN,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNUSED
  }
)
@RequiredArgsConstructor
public class UnreachableCodeDiagnostic extends AbstractVisitorDiagnostic {

  /** Настройки построения графа: рёбра итераций циклов не нужны — недостижимость они не меняют. */
  private static final CfgBuildOptions CFG_OPTIONS = CfgBuildOptions.defaults()
    .withLoopIterations(false)
    .withPreprocessorConditions(true)
    .withAdjacentDeadCode(false);

  private final ControlFlowGraphIndex controlFlowGraphIndex;
  private final CompilationContextResolver compilationContextResolver;

  // кэш диапазонов зарегистрированных ошибок
  private final List<Range> errorRanges = new ArrayList<>();

  // диапазоны препроцессорных скобок
  private final List<Range> preprocessorRanges = new ArrayList<>();

  /** Содержимое заведомо исключённых веток, без ограничивающих их директив. */
  private final List<Range> unreachablePreprocessorRanges = new ArrayList<>();

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    errorRanges.clear();
    preprocessorRanges.clear();
    unreachablePreprocessorRanges.clear();

    var preprocessors = Trees.findAllRuleNodes(ctx, BSLParser.RULE_preprocessor);
    if (preprocessors.isEmpty()) {
      return super.visitFile(ctx);
    }

    var reachability = new PreprocessorReachability(documentContext.getFileType());
    Deque<BSLParser.PreprocessorContext> previousNodes = new ArrayDeque<>();
    for (var tree : preprocessors) {
      var node = (BSLParser.PreprocessorContext) tree;
      if (node.preproc_if() != null) {
        previousNodes.push(node);
        reachability.enterIf(node.preproc_if());
      } else if (node.preproc_elsif() != null) {
        savePreprocessorRange(previousNodes, node, reachability);
        previousNodes.push(node);
        reachability.enterElsif(node.preproc_elsif());
      } else if (node.preproc_else() != null) {
        savePreprocessorRange(previousNodes, node, reachability);
        previousNodes.push(node);
        reachability.enterElse();
      } else if (node.preproc_endif() != null) {
        savePreprocessorRange(previousNodes, node, reachability);
        reachability.enterEndif();
      } else {
        // Области не открывают и не закрывают условные ветки.
      }
    }

    // Неполная структура не задаёт корректных веток и может нарушить построение CFG.
    if (!reachability.isWellFormed()) {
      return ctx;
    }

    super.visitFile(ctx);
    appendUnreachablePreprocessorCode();
    return ctx;
  }

  private void savePreprocessorRange(Deque<BSLParser.PreprocessorContext> nodes,
                                    BSLParser.PreprocessorContext node, PreprocessorReachability reachability) {
    if (!nodes.isEmpty()) {
      var previous = nodes.pop();
      preprocessorRanges.add(
        Ranges.create(
          previous.getStop().getLine(),
          previous.getStop().getCharPositionInLine() + previous.getStop().getText().length() + 1,
          node.getStart().getLine(),
          node.getStart().getCharPositionInLine() - 1));
      if (reachability.isUnreachable(compilationContextResolver.possibleContexts(documentContext, previous)
        .orElse(ExecutionContext.ALL))) {
        addUnreachableBranch(previous, node);
      }
    }
  }

  /**
   * Выделить содержимое ветки, включая комментарии и вложенные области.
   * Пустая ветка или ветка только с комментариями/директивами не содержит недостижимого кода.
   */
  private void addUnreachableBranch(BSLParser.PreprocessorContext opening, BSLParser.PreprocessorContext closing) {
    var tokens = documentContext.getTokens();
    var first = -1;
    var last = -1;
    var hasCode = false;
    for (var i = opening.getStop().getTokenIndex() + 1; i < closing.getStart().getTokenIndex(); i++) {
      var token = tokens.get(i);
      if (token.getLine() <= opening.getStop().getLine() || token.getText().isBlank()) {
        continue;
      }
      if (first < 0) {
        first = i;
      }
      last = i;
      if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != BSLLexer.HASH
        && !BSLLexer.VOCABULARY.getSymbolicName(token.getType()).startsWith("PREPROC_")) {
        hasCode = true;
      }
    }
    if (hasCode) {
      unreachablePreprocessorRanges.add(Ranges.create(tokens.get(first), tokens.get(last)));
    }
  }

  /**
   * Объединить пересекающиеся сообщения с диапазонами исключённых веток.
   * Это также убирает сообщения после Возврат внутри уже целиком недостижимого блока.
   */
  private void appendUnreachablePreprocessorCode() {
    if (unreachablePreprocessorRanges.isEmpty()) {
      return;
    }
    var ranges = new ArrayList<>(unreachablePreprocessorRanges);
    diagnosticStorage.getDiagnostics().forEach(diagnostic -> ranges.add(diagnostic.getRange()));
    diagnosticStorage.clearDiagnostics();
    Ranges.mergeOverlapping(ranges).forEach(diagnosticStorage::addDiagnostic);
  }

  @Override
  public ParseTree visitSubCodeBlock(BSLParser.SubCodeBlockContext ctx) {
    errorRanges.clear();
    super.visitSubCodeBlock(ctx);
    var block = ctx.codeBlock();
    // Незавершённая инструкция препроцессора может оставить subCodeBlock без тела.
    if (block != null) {
      appendUnreachableCode(block);
    }
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    errorRanges.clear();
    super.visitFileCodeBlock(ctx);
    appendUnreachableCode(ctx.codeBlock());
    return ctx;
  }

  private void appendUnreachableCode(BSLParser.CodeBlockContext ctx) {
    var graph = controlFlowGraphIndex.graphOf(documentContext, ctx, CFG_OPTIONS);
    var deadCode = graph.vertexSet().stream()
      .filter(vertex -> vertex != graph.getEntryPoint() && vertex.getClass() != ExitVertex.class)
      .filter(vertex -> graph.inDegreeOf(vertex) == 0)
      .flatMap(vertex -> vertex.getAst().stream())
      .sorted(Comparator.comparingInt(ruleContext -> ruleContext.getStart().getLine()))
      .map(Ranges::create)
      .toList();


    var newRanges = new ArrayList<Range>();
    for (var range : deadCode) {
      var alreadyDetected = false;
      for (Range detectedRange : errorRanges) {
        var pos = new Position(range.getStart().getLine(), range.getStart().getCharacter());
        if (Ranges.containsPosition(detectedRange, pos)) {
          alreadyDetected = true;
          break;
        }
      }
      if (!alreadyDetected) {
        newRanges.add(range);
      }
    }

    for (var range : newRanges) {
      diagnosticStorage.addDiagnostic(range);
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

  private void findAndAddDiagnostic(ParserRuleContext ctx) {

    // если это вложенный в ранее обработанный блок, то исключим из проверки
    var pos = new Position(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    for (Range range : errorRanges) {
      if (Ranges.containsPosition(range, pos)) {
        return;
      }
    }

    var nodeParent = ctx.getParent();
    if (nodeParent == null) {
      return;
    }

    BSLParser.StatementContext ppNode = (BSLParser.StatementContext) nodeParent.getParent();
    if (ppNode == null) {
      return;
    }

    var ppNodeParent = ppNode.getParent();
    if (ppNodeParent == null) {
      return;
    }

    List<ParserRuleContext> statements = Trees.getChildren(ppNodeParent, BSLParser.RULE_statement)
      .stream()
      .filter(node ->
        node.getStart().getType() != BSLLexer.SEMICOLON
          && !Trees.nodeContains(node,
          BSLParser.RULE_regionStart,
          BSLParser.RULE_regionEnd,
          BSLParser.RULE_preproc_endif))
      .toList().reversed();

    // если в блоке кода есть еще стейты кроме текущего
    if (statements.size() > 1) {
      // найдем последний блок
      ParserRuleContext endCurrentBlockNode = getEndCurrentBlockNode(statements, pos);

      // если последний стейт не текущий, значит он будет недостижим
      if (!ppNode.equals(endCurrentBlockNode)) {
        var newRange = Ranges.create(
          statements.get(statements.indexOf(ppNode) - 1).getStart(),
          endCurrentBlockNode.getStop());
        diagnosticStorage.addDiagnostic(newRange);
        // сохраним в кэш
        errorRanges.add(newRange);
      }
    }
  }

  private ParserRuleContext getEndCurrentBlockNode(List<ParserRuleContext> statements, Position pos) {

    // найдем блок препроцессора, в котором лежит наш стейт
    Range preprocRange = null;
    for (Range range : preprocessorRanges.reversed()) {
      if (Ranges.containsPosition(range, pos)) {
        preprocRange = range;
      }
    }

    // т.к. список реверснут, берем первый элемент
    ParserRuleContext endCurrentBlockNode = statements.getFirst();

    if (preprocRange != null) {
      // пройдем по всем стейтам (с конца идем) и ищем первый, находящийся в том же блоке
      // препроцессора, что и стейт прерывания
      for (ParserRuleContext statement : statements) {
        var posStatement = new Position(
          statement.getStart().getLine(),
          statement.getStart().getCharPositionInLine());
        if (Ranges.containsPosition(preprocRange, posStatement)) {
          endCurrentBlockNode = statement;
          break;
        }
      }
    }
    return endCurrentBlockNode;
  }
}
