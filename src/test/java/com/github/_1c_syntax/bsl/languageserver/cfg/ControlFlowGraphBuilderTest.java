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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ControlFlowGraphBuilderTest {

  @Test
  void linearBlockCanBeBuilt() {

    var code = "А = 1; Б = 2; В = 3;";

    var parseTree = parse(code);
    var builder = new ControlFlowGraphBuilder();
    var graph = builder.buildGraph(parseTree);

    var vertices = traverseToOrderedList(graph);
    assertThat(vertices.size()).isEqualTo(2);
    assertThat(vertices.get(0) instanceof BasicBlockVertex).isTrue();
    assertThat(vertices.get(1) instanceof ExitVertex).isTrue();

    var outgoing = graph.outgoingEdgesOf(vertices.get(0));
    assertThat(outgoing.size()).isEqualTo(1);
    var exitVertex = graph.getEdgeTarget((CfgEdge) (outgoing.toArray()[0]));
    assertThat(exitVertex).isEqualTo(vertices.get(1));
  }

  @Test
  void branchingWithOneBranch() {

    var code = "А = 1;\n" +
      "Если Б = 2 Тогда\n" +
      "    В = 4;\n" +
      "КонецЕсли;";

    var parseTree = parse(code);
    var builder = new ControlFlowGraphBuilder();
    var graph = builder.buildGraph(parseTree);

    var vertices = traverseToOrderedList(graph);

    assertThat(vertices.get(0)).isInstanceOf(BasicBlockVertex.class);
    assertThat(graph.getEdge(vertices.get(0), vertices.get(1))).isNotNull();
    assertThat(vertices.get(1)).isInstanceOf(BranchingVertex.class);
    assertThat(graph.outgoingEdgesOf(vertices.get(1)).size()).isEqualTo(2);

    assertThat(vertices.get(2)).isInstanceOf(ExitVertex.class);
    assertThat(graph.incomingEdgesOf(vertices.get(2)).size()).isEqualTo(2);

    var branches = graph.outgoingEdgesOf(vertices.get(1))
      .stream()
      .collect(Collectors.toMap(CfgEdge::getType, graph::getEdgeTarget));

    var trueBlock = (BasicBlockVertex) vertices.get(3);
    assertThat(trueBlock.statements().get(0).getText()).isEqualTo("В=4;");
    assertThat(branches.get(CfgEdgeType.TRUE_BRANCH)).isEqualTo(trueBlock);

    var falseBlock = vertices.get(2);
    assertThat(branches.get(CfgEdgeType.FALSE_BRANCH)).isEqualTo(falseBlock);

  }

  private List<CfgVertex> traverseToOrderedList(ControlFlowGraph graph) {
    var traverse = new DepthFirstIterator<>(graph, graph.getEntryPoint());
    var list = new ArrayList<CfgVertex>();
    traverse.forEachRemaining(list::add);
    return list;
  }

  BSLParser.CodeBlockContext parse(String code) {
    var dContext = TestUtils.getDocumentContext(code);
    return dContext.getAst().fileCodeBlock().codeBlock();
  }
}