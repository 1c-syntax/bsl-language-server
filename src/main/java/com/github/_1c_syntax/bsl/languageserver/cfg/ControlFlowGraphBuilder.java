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
import java.util.Deque;

public class ControlFlowGraphBuilder extends BSLParserBaseVisitor<ParseTree> {

  private ControlFlowGraph graph;
  private Deque<JumpStateRecord> buildStack;
  private Deque<CfgVertex> orphanedVertices;
  private LinearBlockVertex currentBlock;

  private static class JumpStateRecord {
    public CfgVertex loopEnd;
    public CfgVertex loopBegin;
    public CfgVertex methodExit;
    public CfgVertex tryBlock;
  }

  public ControlFlowGraph buildGraph(BSLParser.CodeBlockContext block) {

    buildStack = new ArrayDeque<>();
    orphanedVertices = new ArrayDeque<>();
    graph = new ControlFlowGraph();

    var methodExitState = new JumpStateRecord();
    methodExitState.methodExit = new ExitVertex();
    graph.addVertex(methodExitState.methodExit);
    pushState(methodExitState);

    block.accept(this);

    connectOrphanedNodes(methodExitState.methodExit, 0);

    return graph;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {

    var topVertex = orphanedVertices.peek();
    var vertex = new LinearBlockVertex();
    if(graph.getEntryPoint() == null)
      graph.setEntryPoint(vertex);

    graph.addVertex(vertex);
    if(topVertex != null) {
      graph.addEdge(topVertex, vertex);
    }

    currentBlock = vertex;
    super.visitCodeBlock(ctx);
    orphanedVertices.push(vertex);

    return ctx;
  }

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {
    currentBlock.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitAssignment(BSLParser.AssignmentContext ctx) {
    currentBlock.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitWaitStatement(BSLParser.WaitStatementContext ctx) {
    currentBlock.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitExecuteStatement(BSLParser.ExecuteStatementContext ctx) {
    currentBlock.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitAddHandlerStatement(BSLParser.AddHandlerStatementContext ctx) {
    currentBlock.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitRemoveHandlerStatement(BSLParser.RemoveHandlerStatementContext ctx) {
    currentBlock.addStatement(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    var condition = new BranchingVertex();
    graph.addVertex(condition);

    var node = currentBlock;
    graph.addEdge(node, condition);

    ctx.ifBranch().accept(this);

    var alternativeConditions = ctx.elsifBranch();
    //

    var elseBranch = ctx.elseBranch();
    if(elseBranch != null){
      elseBranch.accept(this);
    }
    else{
      orphanedVertices.push(condition);
    }

    return ctx;

  }

  private void pushState(JumpStateRecord state) {

    var top = buildStack.peek();
    if(top == null){
      buildStack.push(state);
      return;
    }

    if(state.loopEnd != null)
      state.loopEnd = top.loopEnd;

    if(state.loopBegin != null)
      state.loopBegin = top.loopBegin;

    if(state.methodExit != null)
      state.methodExit = top.methodExit;

    if(state.tryBlock != null)
      state.tryBlock = top.tryBlock;

  }

  private JumpStateRecord popState(){
    return buildStack.pop();
  }

  private JumpStateRecord topState() {
    var state = buildStack.peek();
    if(state == null)
      throw new IllegalStateException();

    return state;
  }

  private void connectOrphanedNodes(CfgVertex connectTo, int nestingLevel){
    while (nestingLevel < orphanedVertices.size()) {
      graph.addEdge(orphanedVertices.pop(), connectTo);
    }
  }
}
