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
package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.jgrapht.traverse.DepthFirstIterator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
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
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var vertices = traverseToOrderedList(graph);
    assertThat(vertices.size()).isEqualTo(2);
    assertThat(vertices.get(0)).isInstanceOf(BasicBlockVertex.class);
    assertThat(vertices.get(1)).isInstanceOf(ExitVertex.class);

    var outgoing = graph.outgoingEdgesOf(vertices.get(0));
    assertThat(outgoing.size()).isEqualTo(1);
    var exitVertex = graph.getEdgeTarget((CfgEdge) (outgoing.toArray()[0]));
    assertThat(exitVertex).isEqualTo(vertices.get(1));
  }

  @Test
  void branchingWithOneBranch() {

    var code = """
      А = 1;
      Если Б = 2 Тогда
          В = 4;
      КонецЕсли;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    walker.walkNext();
    assertThat(walker.isOnBranch()).isTrue();
    assertThat(walker.availableRoutes().size()).isEqualTo(2);

    var branch = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=4");

    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);
    assertThat(walker.availableRoutes().size()).isZero();

    var exit = walker.getCurrentNode();
    walker.walkTo(branch);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(walker.getCurrentNode()).isEqualTo(exit);
    assertThat(graph.incomingEdgesOf(exit).size()).isEqualTo(2);

  }

  @Test
  void conditionWithElse() {
    var code = """
      А = 1;
      Если Б = 2 Тогда
          В = 4;
      Иначе
          В = 5;КонецЕсли;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    walker.walkNext();

    assertThat(walker.isOnBranch()).isTrue();
    var fork = walker.getCurrentNode();

    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=4");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);

    walker.walkTo(fork);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=5");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);
  }

  @Test
  void multipleConditionsTest() {
    var code = """
      Если Б = 1 Тогда
          В = 1;
      ИначеЕсли Б = 2 Тогда
          В = 2;
      ИначеЕсли Б = 3 Тогда
          В = 3;Иначе
          В = 4;КонецЕсли;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();

    assertThat(walker.isOnBranch()).isTrue();
    var topCondition = walker.getCurrentNode();

    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=1");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);

    walker.walkTo(topCondition);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(walker.isOnBranch()).isTrue();
    var cond = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=2");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);

    walker.walkTo(cond);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    cond = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=3");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);

    walker.walkTo(cond);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=4");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);

  }

  @Test
  void whileLoopTest() {
    var code = """
      А = 1;
      Пока Б = 1 Цикл
          В = 1;
      КонецЦикла;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(walker.isOnBranch()).isFalse();

    walker.walkNext();
    assertThat(walker.isOnBranch()).isTrue();
    assertThat(walker.getCurrentNode()).isInstanceOf(WhileLoopVertex.class);
    var loopStart = (WhileLoopVertex) walker.getCurrentNode();

    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=1");
    walker.walkNext(CfgEdgeType.LOOP_ITERATION);
    assertThat(walker.getCurrentNode()).isEqualTo(loopStart);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);

    var ast = loopStart.getAst();
    var expression = loopStart.getExpression();
    assertThat(ast).isPresent();
    assertThat(ast.get()).isInstanceOf(BSLParser.WhileStatementContext.class);
    assertThat(expression).isInstanceOf(BSLParser.ExpressionContext.class);
    assertThat(expression.getParent()).isEqualTo(ast.get());

  }

  @Test
  void testInnerLoops() {

    var code = """
      А = 1;
      Пока Б = 1 Цикл
         В = 1;
         Если А = 1 Тогда
           Продолжить;
         КонецЕсли;
         Для Сч = 1 По 5 Цикл
           Б = 1;
           Прервать;
           В = 2;
         КонецЦикла;
         Прервано = Истина;
      КонецЦикла;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    builder.determineAdjacentDeadCode(true);
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    walker.walkNext();
    var firstLoopStart = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=1");
    walker.walkNext();
    var condition = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isEqualTo(firstLoopStart);
    walker.walkTo(condition);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(walker.isOnBranch()).isTrue();
    assertThat(walker.getCurrentNode()).isInstanceOf(ForLoopVertex.class);

    var secondLoopStart = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=1");
    assertThat(graph.outDegreeOf(walker.getCurrentNode())).isEqualTo(2);
    walker.walkNext();
    assertThat(textOfCurrentNode(walker)).isEqualTo("Прервано=Истина");

    var secondLoopEnd = walker.getCurrentNode();

    assertThat(graph.getEdge(secondLoopStart, secondLoopEnd).getType()).isEqualTo(CfgEdgeType.FALSE_BRANCH);

    // входящих - 2. переход из головы цикла и переход из блока до Прервать
    assertThat(graph.incomingEdgesOf(secondLoopEnd).size()).isEqualTo(2);
    var edgeOfBreak = graph.incomingEdgesOf(secondLoopEnd)
      .stream()
      .filter(x -> x.getType() == CfgEdgeType.DIRECT)
      .findFirst();

    assertThat(edgeOfBreak).isPresent();
    walker.walkTo(secondLoopStart);
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(graph.outgoingEdgesOf(walker.getCurrentNode()).contains(edgeOfBreak.get())).isTrue();

    // LOOP от мертвого куска существует
    assertThat(graph.incomingEdgesOf(secondLoopStart).isEmpty()).isFalse();

    walker.walkTo(secondLoopEnd);
    walker.walkNext(CfgEdgeType.LOOP_ITERATION);
    assertThat(walker.getCurrentNode()).isEqualTo(firstLoopStart);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);
  }

  @Test
  void tryHandlerFlowTest() {
    var code = """
      Попытка
         А = 1;
      Исключение
         Б = 1;
      КонецПопытки""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(walker.isOnBranch()).isTrue();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("А=1");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);

    walker.start();
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=1");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);
  }

  @Test
  void linearBlockWithLabel() {
    var code = """
      А = 1;
      Б = 2;
      ~Прыг:
      В = 4;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(LabelVertex.class);
    assertThat(((LabelVertex) walker.getCurrentNode()).getLabelName()).isEqualTo("Прыг");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=4");
    walker.walkNext();
    assertThat(walker.availableRoutes()).isEmpty();
  }

  @Test
  void linearBlockWithJumpToLabel() {
    var code = """
      А = 1;
      Б = 2;
      ~Прыг:
      В = 4;
      Перейти ~Прыг;
      МертвыйКод = Истина;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(LabelVertex.class);
    assertThat(((LabelVertex) walker.getCurrentNode()).getLabelName()).isEqualTo("Прыг");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(LabelVertex.class);
  }

  @Test
  void hardcoreCrazyJumpingTest() {

    var code = getResourceFile("hardcoreCrazyJumpingTest");

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    // пока пусть хотя бы просто не падает.
    assertThat(graph.vertexSet()).isNotEmpty();

    var list = graph.vertexSet().stream()
      .filter(x -> x instanceof BasicBlockVertex)
      .filter(x -> ((BasicBlockVertex) x).statements().isEmpty())
      .collect(Collectors.toList());

    assertThat(list).isEmpty();
    assertThat(graph.vertexSet()).hasSize(18);
  }

  @Test
  void preprocessorSingleIfBranching() {
    var code = """
      А = 1;
      #Если Сервер Тогда
         Б = 2;
      #КонецЕсли
      В = 3;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(textOfCurrentNode(walker)).isEqualTo("А=1");
    walker.walkNext();
    assertThat(walker.isOnBranch()).isTrue();
    var ifNode = walker.getCurrentNode();

    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=2");
    walker.walkNext();
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=3");
    var lastStatement = walker.getCurrentNode();
    walker.walkTo(ifNode);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(walker.getCurrentNode()).isSameAs(lastStatement);
  }

  @Test
  void preprocessorIfWithElseBranching() {
    var code = """
      А = 1;
      #Если Сервер Тогда
         Б = 2;
      #Иначе
         Б = 3;#КонецЕсли
      В = 3;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(textOfCurrentNode(walker)).isEqualTo("А=1");
    walker.walkNext();
    assertThat(walker.isOnBranch()).isTrue();
    var ifNode = walker.getCurrentNode();

    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=2");
    walker.walkNext();
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=3");
    var lastStatement = walker.getCurrentNode();
    walker.walkTo(ifNode);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=3");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isSameAs(lastStatement);
  }

  @Test
  void preprocessorIfWithElseIfBranching() {
    var code = """
      А = 1;
      #Если Сервер Тогда
         Б = 2;
      #ИначеЕсли ВебКлиент Тогда
         Б = 3;
      #ИначеЕсли МобильныйКлиент Тогда
         Б = 4;
      #Иначе
         Б = 5;#КонецЕсли
      В = 3;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(textOfCurrentNode(walker)).isEqualTo("А=1");
    walker.walkNext();
    assertThat(walker.isOnBranch()).isTrue();
    var ifNode = walker.getCurrentNode();

    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=2");
    walker.walkNext();
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=3");
    var lastStatement = walker.getCurrentNode();

    walker.walkTo(ifNode);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(walker.isOnBranch()).isTrue();
    ifNode = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=3");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isSameAs(lastStatement);
    walker.walkTo(ifNode);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);

    assertThat(walker.isOnBranch()).isTrue();
    ifNode = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=4");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isSameAs(lastStatement);
    walker.walkTo(ifNode);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Б=5");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isSameAs(lastStatement);

  }

  @SneakyThrows
  private String getResourceFile(String name) {

    String filePath = "cfg/" + name + ".bsl";

    return IOUtils.resourceToString(
      filePath,
      StandardCharsets.UTF_8,
      this.getClass().getClassLoader()
    );

  }

  private List<CfgVertex> traverseToOrderedList(ControlFlowGraph graph) {
    assertThat(graph.getEntryPoint()).isNotNull();
    var traverse = new DepthFirstIterator<>(graph, graph.getEntryPoint());
    var list = new ArrayList<CfgVertex>();
    traverse.forEachRemaining(list::add);
    return list;
  }

  BSLParser.CodeBlockContext parse(String code) {
    var dContext = TestUtils.getDocumentContext(code);
    return dContext.getAst().fileCodeBlock().codeBlock();
  }

  private String textOfCurrentNode(ControlFlowGraphWalker walker) {
    var block = (BasicBlockVertex) walker.getCurrentNode();
    return block.statements().get(0).getText();
  }
}