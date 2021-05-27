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

public class CfgBuildingParseTreeVisitor extends BSLParserBaseVisitor<ParseTree> {

  private StatementsBlockWriter blocks;
  private ControlFlowGraph graph;

  public ControlFlowGraph buildGraph(BSLParser.CodeBlockContext block) {

    blocks = new StatementsBlockWriter();
    graph = new ControlFlowGraph();

    var exitPoints = new StatementsBlockWriter.JumpInformationRecord();
    exitPoints.methodReturn = new ExitVertex();
    graph.addVertex(exitPoints.methodReturn);

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

  private void removeOrphanedNodes() {
    graph.vertexSet().stream()
      .filter(x -> graph.edgesOf(x).isEmpty() && !(x instanceof ExitVertex))
      .forEach(x -> graph.removeVertex(x));
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx) {
    var compound = ctx.compoundStatement();
    if (compound != null) {
      return super.visitStatement(ctx);
    }

    blocks.addStatement(ctx);

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
      graph.addEdge(currentLevelBlock.getBuildParts().pop(), upperBlock.end());
    }

    return ctx;
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
}
