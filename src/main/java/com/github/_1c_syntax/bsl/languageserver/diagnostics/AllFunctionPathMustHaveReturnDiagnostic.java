/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.cfg.BasicBlockVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildingParseTreeVisitor;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgEdgeType;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ConditionalVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.LoopVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.WhileLoopVertex;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.UNPREDICTABLE,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.SUSPICIOUS
  }
)
public class AllFunctionPathMustHaveReturnDiagnostic extends AbstractVisitorDiagnostic {

  private static final boolean LOOPS_EXECUTED_ONCE_DEFAULT = true;
  private static final boolean IGNORE_ELSELESS_SWITCHES = false;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + LOOPS_EXECUTED_ONCE_DEFAULT
  )
  private boolean loopsExecutedAtLeastOnce = LOOPS_EXECUTED_ONCE_DEFAULT;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + IGNORE_ELSELESS_SWITCHES
  )
  private boolean ignoreMissingElseOnExit = IGNORE_ELSELESS_SWITCHES;

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {

    if (ctx.ENDFUNCTION_KEYWORD() == null) {
      return ctx;
    }

    // Исключаем дублирование с диагностикой FunctionShouldHaveReturnDiagnostic
    // проверяем только методы у которых есть хоть один возврат
    Collection<ParseTree> tokens = Trees.findAllTokenNodes(ctx, BSLLexer.RETURN_KEYWORD);
    if (tokens.isEmpty()) {
      return ctx;
    }

    checkAllPathsHaveReturns(ctx);

    return ctx;

  }

  private void checkAllPathsHaveReturns(BSLParser.FunctionContext ctx) {
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    var graph = builder.buildGraph(ctx.subCodeBlock().codeBlock());

    var exitNode = graph.getExitPoint();

    var incomingVertices = graph.incomingEdgesOf(exitNode).stream()
      .map(graph::getEdgeSource)
      .map(vertex -> nonExplicitReturnNode(vertex, graph))
      .flatMap(Optional::stream)
      .toList();

    if (incomingVertices.isEmpty()) {
      return;
    }

    var listOfMessages = new ArrayList<DiagnosticRelatedInformation>();
    listOfMessages.add(RelatedInformation.create(documentContext.getUri(),
      Ranges.create(ctx.funcDeclaration().subName()),
      info.getMessage()));

    incomingVertices.stream()
      .map(vertex -> RelatedInformation.create(documentContext.getUri(),
        Ranges.create(vertex),
        info.getMessage()))
      .collect(Collectors.toCollection(() -> listOfMessages));

    diagnosticStorage.addDiagnostic(ctx.funcDeclaration().subName(), listOfMessages);

  }

  private Optional<ParserRuleContext> nonExplicitReturnNode(CfgVertex v, ControlFlowGraph graph) {
    if (v instanceof BasicBlockVertex basicBlock) {
      return checkBasicBlockExitingNode(basicBlock);
    } else if (v instanceof LoopVertex loop) {
      return checkLoopExitingNode(loop);
    } else if (v instanceof ConditionalVertex conditional) {
      return checkElseIfClauseExitingNode(conditional, graph);
    }

    return v.getAst();
  }

  private Optional<ParserRuleContext> checkElseIfClauseExitingNode(ConditionalVertex v, ControlFlowGraph graph) {
    // check if this vertex connected to exit by FALSE branch
    var edgeOrNot = graph.getAllEdges(v, graph.getExitPoint()).stream()
      .filter(edge -> edge.getType() == CfgEdgeType.FALSE_BRANCH)
      .findAny();

    if (edgeOrNot.isEmpty()) {
      return Optional.empty();
    }

    var expression = v.getExpression();
    if (expression.getParent() instanceof BSLParser.ElsifBranchContext elsifBranch && !ignoreMissingElseOnExit) {
      return Optional.of(elsifBranch.getParent());
    }

    return Optional.empty();
  }

  private static Optional<ParserRuleContext> checkBasicBlockExitingNode(BasicBlockVertex block) {
    if (!block.statements().isEmpty()) {
      var lastStatement = block.statements().get(block.statements().size() - 1);

      var nodes = Trees.findAllRuleNodes(lastStatement, BSLParser.RULE_returnStatement, BSLParser.RULE_raiseStatement);
      if (nodes.isEmpty()) {
        return block.getAst();
      }
    }
    return Optional.empty();
  }

  private Optional<ParserRuleContext> checkLoopExitingNode(LoopVertex v) {
    if (v instanceof WhileLoopVertex whileLoop && isEndlessLoop(whileLoop)) {
      return Optional.empty();
    }

    if (loopsExecutedAtLeastOnce) {
      // из цикла в exit может прийти только falseBranch или пустое тело цикла
      // и то и другое не нужно нам в рамках диагностики
      return Optional.empty();
    }
    return v.getAst();
  }

  private static boolean isEndlessLoop(WhileLoopVertex whileLoop) {
    var expression = whileLoop.getExpression();
    return expression.getChildCount() == 1
      && expression.member(0).constValue() != null
      && expression.member(0).constValue().TRUE() != null;
  }
}
