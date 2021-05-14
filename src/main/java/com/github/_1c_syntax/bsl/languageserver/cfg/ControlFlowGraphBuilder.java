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
  private Deque<CfgVertex> buildStack;
  private ExitVertex exit;

  public ControlFlowGraph buildGraph(BSLParser.CodeBlockContext block) {

    exit = new ExitVertex();
    buildStack = new ArrayDeque<>();
    graph = new ControlFlowGraph();

    block.accept(this);

    graph.addVertex(exit);
    graph.addEdge(buildStack.pop(), exit);

    return graph;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {
    var topVertex = buildStack.peek();
    var vertex = new LinearBlockVertex();
    buildStack.push(vertex);
    super.visitCodeBlock(ctx);
    graph.addVertex(vertex);

    if(topVertex != null) {
      graph.addEdge(topVertex, vertex);
    }

    return ctx;
  }
}
