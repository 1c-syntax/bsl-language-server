/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CfgBuildingParseTreeVisitor extends BSLParserBaseVisitor<ParseTree> {

  private StatementsBlockWriter blocks;
  private ControlFlowGraph graph;
  private Map<String, LabelVertex> jumpLabels;

  public ControlFlowGraph buildGraph(BSLParser.CodeBlockContext block) {

    blocks = new StatementsBlockWriter();
    graph = new ControlFlowGraph();
    jumpLabels = new HashMap<>();

    var exitPoints = new StatementsBlockWriter.JumpInformationRecord();
    exitPoints.methodReturn = graph.getExitPoint();
    exitPoints.exceptionHandler = exitPoints.methodReturn;

    blocks.enterBlock(exitPoints);

    block.accept(this);

    var builtBlock = blocks.leaveBlock();
    graph.addVertex(builtBlock.begin());
    graph.addVertex(builtBlock.end());

    connectGraphTail(builtBlock, exitPoints.methodReturn);
    removeOrphanedNodes();

    if (graph.containsVertex(builtBlock.begin())) {
      graph.setEntryPoint(builtBlock.begin());
    } else {
      graph.setEntryPoint(exitPoints.methodReturn);
    }

    return graph;
  }

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {
    blocks.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitWaitStatement(BSLParser.WaitStatementContext ctx) {
    blocks.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitAssignment(BSLParser.AssignmentContext ctx) {
    blocks.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {

    var conditionStatement = new ConditionalVertex(ctx.ifBranch().expression());
    graph.addVertex(conditionStatement);

    connectGraphTail(blocks.getCurrentBlock(), conditionStatement);

    // подграф if
    blocks.enterBlock();
    var currentLevelBlock = blocks.getCurrentBlock();

    // тело true
    blocks.enterBlock();
    ctx.ifBranch().codeBlock().accept(this);
    var truePart = blocks.leaveBlock();

    graph.addEdge(conditionStatement, truePart.begin(), CfgEdgeType.TRUE_BRANCH);
    currentLevelBlock.getBuildParts().push(truePart.end());
    currentLevelBlock.getBuildParts().push(conditionStatement);

    // ветки elseif
    for (var elif : ctx.elsifBranch()) {
      elif.accept(this);
    }

    if (ctx.elseBranch() != null) {
      ctx.elseBranch().accept(this);
      conditionStatement = null;
    } else {
      // убрали условие верхнего уровня
      conditionStatement = (ConditionalVertex) currentLevelBlock.getBuildParts().pop();
    }

    // конец подграфа if
    blocks.leaveBlock();

    var upperBlock = blocks.getCurrentBlock();
    upperBlock.split();

    graph.addVertex(upperBlock.end());
    if (conditionStatement != null) {
      graph.addEdge(conditionStatement, upperBlock.end(), CfgEdgeType.FALSE_BRANCH);
    }

    while (!currentLevelBlock.getBuildParts().isEmpty()) {
      var blockTail = currentLevelBlock.getBuildParts().pop();
      if (graph.incomingEdgesOf(blockTail).isEmpty() && blockTail instanceof BasicBlockVertex) {
        // это мертвый код. Он может быть пустым блоком
        // тогда он не нужен сам по себе
        var basicBlock = (BasicBlockVertex) blockTail;
        if (basicBlock.statements().isEmpty())
          continue;
      }
      graph.addEdge(blockTail, upperBlock.end());
    }

    return ctx;
  }

  @Override
  public ParseTree visitElsifBranch(BSLParser.ElsifBranchContext ctx) {

    var previousCondition = blocks.getCurrentBlock().getBuildParts().pop();

    var condition = new ConditionalVertex(ctx.expression());
    graph.addVertex(condition);
    graph.addEdge(previousCondition, condition, CfgEdgeType.FALSE_BRANCH);

    // тело true
    blocks.enterBlock();
    ctx.codeBlock().accept(this);
    var truePart = blocks.leaveBlock();

    graph.addEdge(condition, truePart.begin(), CfgEdgeType.TRUE_BRANCH);
    blocks.getCurrentBlock().getBuildParts().push(truePart.end());
    blocks.getCurrentBlock().getBuildParts().push(condition);

    return ctx;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {
    var currentBlock = blocks.getCurrentBlock();
    graph.addVertex(currentBlock.begin());
    return super.visitCodeBlock(ctx);
  }

  @Override
  public ParseTree visitElseBranch(BSLParser.ElseBranchContext ctx) {
    blocks.enterBlock();
    ctx.codeBlock().accept(this);
    var block = blocks.leaveBlock();

    // на стеке находится условие
    var condition = blocks.getCurrentBlock().getBuildParts().pop();
    graph.addEdge(condition, block.begin(), CfgEdgeType.FALSE_BRANCH);
    blocks.getCurrentBlock().getBuildParts().push(block.end());

    return ctx;
  }

  @Override
  public ParseTree visitWhileStatement(BSLParser.WhileStatementContext ctx) {
    var loopStart = new WhileLoopVertex(ctx.expression());
    buildLoopSubgraph(ctx.codeBlock(), loopStart);
    return ctx;
  }

  @Override
  public ParseTree visitForStatement(BSLParser.ForStatementContext ctx) {
    var loopStart = new ForLoopVertex(ctx);
    buildLoopSubgraph(ctx.codeBlock(), loopStart);
    return ctx;
  }

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
    var loopStart = new ForeachLoopVertex(ctx);
    buildLoopSubgraph(ctx.codeBlock(), loopStart);
    return ctx;
  }

  @Override
  public ParseTree visitExecuteStatement(BSLParser.ExecuteStatementContext ctx) {
    blocks.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitAddHandlerStatement(BSLParser.AddHandlerStatementContext ctx) {
    blocks.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitRemoveHandlerStatement(BSLParser.RemoveHandlerStatementContext ctx) {
    blocks.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitBreakStatement(BSLParser.BreakStatementContext ctx) {
    var jumps = blocks.getCurrentBlock().getJumpContext();
    makeJump(jumps.loopBreak);
    return ctx;
  }

  @Override
  public ParseTree visitGotoStatement(BSLParser.GotoStatementContext ctx) {

    blocks.addStatement(ctx);

    var name = ctx.labelName().getText();
    var labelVertex = createOrGetKnownLabel(name);

    var block = blocks.getCurrentBlock();
    connectGraphTail(block, labelVertex);

    // создадим новый end, в который будут помещаться все будущие строки
    block.split();
    graph.addVertex(block.end());

    return ctx;
  }

  @Override
  public ParseTree visitLabel(BSLParser.LabelContext ctx) {
    var name = ctx.labelName().getText();
    var labelVertex = createOrGetKnownLabel(name);
    var block = blocks.getCurrentBlock();

    // это может быть пустой блок/первая метка в блоке
    // корректно соединим с заменой пустого блока на эту вершину
    connectGraphTail(block, labelVertex);

    // создадим новый end, в который будут помещаться все будущие строки
    block.split();
    graph.addVertex(block.end());

    // присоединим ребро от вершины-метки к вершине-продолжению
    graph.addEdge(labelVertex, block.end());

    return ctx;
  }

  @Override
  public ParseTree visitContinueStatement(BSLParser.ContinueStatementContext ctx) {
    var jumps = blocks.getCurrentBlock().getJumpContext();
    makeJump(jumps.loopContinue);
    return ctx;
  }

  @Override
  public ParseTree visitReturnStatement(BSLParser.ReturnStatementContext ctx) {
    blocks.addStatement(ctx);
    var jumps = blocks.getCurrentBlock().getJumpContext();
    makeJump(jumps.methodReturn);
    return ctx;
  }

  @Override
  public ParseTree visitTryStatement(BSLParser.TryStatementContext ctx) {

    var tryBranch = new TryExceptVertex(ctx);
    graph.addVertex(tryBranch);
    connectGraphTail(blocks.getCurrentBlock(), tryBranch);

    // весь блок try
    blocks.enterBlock();

    blocks.enterBlock();
    ctx.exceptCodeBlock().accept(this);
    var exception = blocks.leaveBlock();

    var jumpInfo = new StatementsBlockWriter.JumpInformationRecord();
    jumpInfo.exceptionHandler = exception.begin();

    blocks.enterBlock(jumpInfo);
    ctx.tryCodeBlock().accept(this);
    var success = blocks.leaveBlock();

    graph.addEdge(tryBranch, success.begin(), CfgEdgeType.TRUE_BRANCH);
    blocks.getCurrentBlock().getBuildParts().push(success.end());

    graph.addEdge(tryBranch, exception.begin(), CfgEdgeType.FALSE_BRANCH);
    blocks.getCurrentBlock().getBuildParts().push(exception.end());

    var builtBlock = blocks.leaveBlock();

    blocks.getCurrentBlock().split();
    graph.addVertex(blocks.getCurrentBlock().end());
    while (!builtBlock.getBuildParts().isEmpty()) {
      graph.addEdge(builtBlock.getBuildParts().pop(), blocks.getCurrentBlock().end());
    }

    return ctx;
  }

  @Override
  public ParseTree visitRaiseStatement(BSLParser.RaiseStatementContext ctx) {
    blocks.addStatement(ctx);
    var jumps = blocks.getCurrentBlock().getJumpContext();
    makeJump(jumps.exceptionHandler);
    return ctx;
  }

  private void makeJump(CfgVertex jumpTarget) {
    connectGraphTail(blocks.getCurrentBlock(), jumpTarget);
    blocks.getCurrentBlock().split();
    graph.addVertex(blocks.getCurrentBlock().end());
  }

  private void buildLoopSubgraph(BSLParser.CodeBlockContext ctx, LoopVertex loopStart) {
    graph.addVertex(loopStart);
    connectGraphTail(blocks.getCurrentBlock(), loopStart);

    blocks.getCurrentBlock().split();
    graph.addVertex(blocks.getCurrentBlock().end());

    var jumpState = new StatementsBlockWriter.JumpInformationRecord();
    jumpState.loopContinue = loopStart;
    jumpState.loopBreak = blocks.getCurrentBlock().end();

    blocks.enterBlock(jumpState);

    ctx.accept(this);

    var body = blocks.leaveBlock();

    graph.addEdge(loopStart, body.begin(), CfgEdgeType.TRUE_BRANCH);
    graph.addEdge(loopStart, blocks.getCurrentBlock().end(), CfgEdgeType.FALSE_BRANCH);
    graph.addEdge(body.end(), loopStart, CfgEdgeType.LOOP_ITERATION);
  }

  private void connectGraphTail(StatementsBlockWriter.StatementsBlockRecord currentBlock, CfgVertex vertex) {

    if (!(currentBlock.end() instanceof BasicBlockVertex)) {
      graph.addEdge(currentBlock.end(), vertex);
      return;
    }

    var currentTail = (BasicBlockVertex) currentBlock.end();
    if (currentTail.statements().isEmpty()) {
      // перевести все связи на новую вершину
      var incoming = graph.incomingEdgesOf(currentTail);
      for (var edge : incoming) {
        var source = graph.getEdgeSource(edge);
        graph.addEdge(source, vertex, edge.getType());
      }
      graph.removeVertex(currentTail);

      // заменить в текущем блоке хвост на новую вершину
      currentBlock.replaceEnd(vertex);
    } else {
      graph.addEdge(currentBlock.end(), vertex);
    }
  }

  private void removeOrphanedNodes() {
    var orphans = graph.vertexSet().stream()
      .filter(x -> graph.edgesOf(x).isEmpty() && !(x instanceof ExitVertex))
      .collect(Collectors.toList());

    // в одном стриме бывает ConcurrentModificationException
    // делаем через другую коллекцию
    orphans.forEach(x -> graph.removeVertex(x));
  }

  private LabelVertex createOrGetKnownLabel(String labelName) {
    var labelVertex = jumpLabels.get(labelName);
    if (labelVertex == null) {
      labelVertex = new LabelVertex(labelName);
      jumpLabels.put(labelName, labelVertex);
      graph.addVertex(labelVertex);
    }

    return labelVertex;
  }
}
