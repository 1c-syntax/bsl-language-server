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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class ControlFlowGraphBuilder extends BSLParserBaseVisitor<ParseTree> {

  private ControlFlowGraph graph;
  private Deque<BuildStateRecord> buildStack;
  private Deque<CfgVertex> verticesInBuild;

  private static class BuildStateRecord {
    public CfgVertex loopEnd;
    public CfgVertex loopBegin;
    public CfgVertex methodExit;
    public CfgVertex tryBlock;
  }

  public ControlFlowGraph buildGraph(BSLParser.CodeBlockContext block) {

    buildStack = new ArrayDeque<>();
    verticesInBuild = new ArrayDeque<>();
    graph = new ControlFlowGraph();

    var methodExitState = new BuildStateRecord();
    methodExitState.methodExit = new ExitVertex();
    graph.addVertex(methodExitState.methodExit);
    pushState(methodExitState);

    block.accept(this);
    var lastBlock = verticesInBuild.pop();
    if (((BasicBlockVertex) lastBlock).statements().isEmpty()) {
      for (CfgEdge edge : graph.incomingEdgesOf(lastBlock)) {
        graph.addEdge(graph.getEdgeSource(edge), methodExitState.methodExit, new CfgEdge(edge.getType()));
      }

      graph.removeVertex(lastBlock);
    } else {
      graph.addEdge(lastBlock, methodExitState.methodExit);
    }
    popState();

    return graph;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {

    startNewBlock();

    super.visitCodeBlock(ctx);

    return ctx;
  }

  private void startNewBlock() {
    var block = new BasicBlockVertex();
    if (graph.getEntryPoint() == null)
      graph.setEntryPoint(block);

    graph.addVertex(block);
    verticesInBuild.push(block);
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx) {
    var compound = ctx.compoundStatement();
    if (compound != null) {
      return super.visitStatement(ctx);
    }

    getStatementsBlock().addStatement(ctx);

    return ctx;
  }

  @Override
  public ParseTree visitExecuteStatement(BSLParser.ExecuteStatementContext ctx) {
    getStatementsBlock().addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitAddHandlerStatement(BSLParser.AddHandlerStatementContext ctx) {
    getStatementsBlock().addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitRemoveHandlerStatement(BSLParser.RemoveHandlerStatementContext ctx) {
    getStatementsBlock().addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    var condition = new BranchingVertex(ctx.ifBranch().expression());

    var top = verticesInBuild.pop();
    graph.addVertex(condition);
    graph.addEdge(top, condition);

    verticesInBuild.push(condition);

    ctx.ifBranch().codeBlock().accept(this);
    var trueBody = verticesInBuild.pop();
    graph.addEdge(condition, trueBody, new CfgEdge(CfgEdgeType.TRUE_BRANCH));

    var alternatives = ctx.elsifBranch();
    for (var alternative : alternatives) {
      alternative.accept(this);
    }

    var elseBranch = ctx.elseBranch();
    if (elseBranch != null) {
      elseBranch.accept(this);
    }

    var falsePart = verticesInBuild.pop();

    if (falsePart == condition) {
      startNewBlock();
      graph.addEdge(condition, verticesInBuild.peek(), new CfgEdge(CfgEdgeType.FALSE_BRANCH));
    } else {
      verticesInBuild.pop(); // убрали condition со стека
      graph.addEdge(condition, falsePart, new CfgEdge(CfgEdgeType.FALSE_BRANCH));
      startNewBlock();
    }

    graph.addEdge(trueBody, verticesInBuild.peek());

    return ctx;

  }

  @Override
  public ParseTree visitElsifBranch(BSLParser.ElsifBranchContext ctx) {

    var condition = new BranchingVertex(ctx.expression());
    ctx.codeBlock().accept(this);
    var block = verticesInBuild.pop();
    graph.addEdge(condition, block, new CfgEdge(CfgEdgeType.TRUE_BRANCH));

    verticesInBuild.push(condition);

    return ctx;
  }

  @Override
  public ParseTree visitElseBranch(BSLParser.ElseBranchContext ctx) {
    ctx.codeBlock().accept(this);
    return ctx;
  }

  private void pushState(BuildStateRecord state) {

    var top = buildStack.peek();
    if (top == null) {
      buildStack.push(state);
      return;
    }

    if (state.loopEnd != null)
      state.loopEnd = top.loopEnd;

    if (state.loopBegin != null)
      state.loopBegin = top.loopBegin;

    if (state.methodExit != null)
      state.methodExit = top.methodExit;

    if (state.tryBlock != null)
      state.tryBlock = top.tryBlock;

  }

  private BasicBlockVertex getStatementsBlock() {
    var item = verticesInBuild.peek();
    assert item != null;

    return (BasicBlockVertex) item;
  }

  private BuildStateRecord popState() {
    return buildStack.pop();
  }

  private BuildStateRecord topState() {
    var state = buildStack.peek();
    if (state == null)
      throw new IllegalStateException();

    return state;
  }

  private void connectOrphanedNodes(CfgVertex connectTo, int nestingLevel) {
    while (nestingLevel < verticesInBuild.size()) {
      graph.addEdge(verticesInBuild.pop(), connectTo);
    }
  }
}
