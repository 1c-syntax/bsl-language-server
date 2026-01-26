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
package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.SneakyThrows;
import org.antlr.v4.runtime.CommonToken;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.jgrapht.traverse.DepthFirstIterator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class ControlFlowGraphBuilderTest {

  @Test
  void testBreakContinueOutsideLoop() {

    var code = """
      Если Истина Тогда
        А = 1;
        Прервать;
        Б = 2;
      КонецЕсли;
      В = 1;
      Продолжить;
      Г = 7;
      """;

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);
    var vertices = traverseToOrderedList(graph);
    assertThat(vertices).isNotEmpty();

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(walker.getCurrentNode()).isInstanceOf(ConditionalVertex.class);
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    // Единый блок без разрыва в месте оператора перехода
    assertThat(((BasicBlockVertex)walker.getCurrentNode()).statements()).hasSize(3);
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    // Единый блок без разрыва в месте оператора перехода
    assertThat(((BasicBlockVertex)walker.getCurrentNode()).statements()).hasSize(3);
    walker.walkNext();

    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);
  }

  @Test
  void PreprocCanBeBuild() {

    var code = """
      Если А = 1 Тогда
      #Если Сервер Тогда
      ИначеЕсли А = 2 Тогда
      #КонецЕсли
      Иначе
      КонецЕсли;""";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);
    var vertices = traverseToOrderedList(graph);
    assertThat(vertices).isNotEmpty(); // or empty...
  }

  @Test
  void linearBlockCanBeBuilt() {

    var code = "А = 1; Б = 2.; В = 3;";

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    var graph = builder.buildGraph(parseTree);

    var vertices = traverseToOrderedList(graph);
    assertThat(vertices).hasSize(2);
    assertThat(vertices.get(0)).isInstanceOf(BasicBlockVertex.class);
    assertThat(vertices.get(1)).isInstanceOf(ExitVertex.class);

    var outgoing = graph.outgoingEdgesOf(vertices.get(0));
    assertThat(outgoing).hasSize(1);
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
    assertThat(walker.availableRoutes()).hasSize(2);

    var branch = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(walker.getCurrentNode()).isInstanceOf(BasicBlockVertex.class);
    assertThat(textOfCurrentNode(walker)).isEqualTo("В=4");

    walker.walkNext();
    assertThat(walker.getCurrentNode()).isInstanceOf(ExitVertex.class);
    assertThat(walker.availableRoutes()).isEmpty();

    var exit = walker.getCurrentNode();
    walker.walkTo(branch);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(walker.getCurrentNode()).isEqualTo(exit);
    assertThat(graph.incomingEdgesOf(exit)).hasSize(2);

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
    assertThat(graph.incomingEdgesOf(secondLoopEnd)).hasSize(2);
    var edgeOfBreak = graph.incomingEdgesOf(secondLoopEnd)
      .stream()
      .filter(x -> x.getType() == CfgEdgeType.DIRECT)
      .findFirst();

    assertThat(edgeOfBreak).isPresent();
    walker.walkTo(secondLoopStart);
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(graph.outgoingEdgesOf(walker.getCurrentNode())).contains(edgeOfBreak.get());

    // LOOP от мертвого куска существует
    assertThat(graph.incomingEdgesOf(secondLoopStart)).isNotEmpty();

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
      .filter(BasicBlockVertex.class::isInstance)
      .filter(x -> ((BasicBlockVertex) x).statements().isEmpty())
      .toList();

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

  @Test
  void test_shouldConnectTopLevelPreprocToSingleFileCodeBlock() {
    var code = """
      #Если Не ВебКлиент Тогда
        Возврат ПустойМассив;
      #КонецЕсли
      """;

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();

    assertThat(walker.isOnBranch()).isTrue();
  }

  @Test
  void test_shouldIgnoreTopLevelPreprocOfVariablesSection() {
    var code = """
      #Если Не ВебКлиент Тогда
      #КонецЕсли
      
      Перем А;
      
      А = 8;
      """;

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();

    assertThat(walker.isOnBranch()).isFalse();
  }

  @Test
  void test_shouldHandleModuleBodyFirstPreprocessor() {
    var code = """
      Процедура А()
         #Если Не ВебКлиент Тогда
              М = 1;
          #КонецЕсли
      КонецПроцедуры
      """;

    var dContext = TestUtils.getDocumentContext(code);
    var parseTree = dContext.getAst().subs().sub(0).procedure().subCodeBlock().codeBlock();

    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();

    assertThat(walker.isOnBranch()).isTrue();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("М=1");
  }

  @Test
  void preprocessorTestBranchingWithExiting()
  {
    var code = """
      #Если Не ВебКлиент Тогда
        Массив = Новый Массив;
        Если Условие Тогда
            Возврат Массив;
        КонецЕсли;
        Возврат ПустойМассив;
      #Иначе
        ВызватьИсключение "Упс";
      #КонецЕсли
      """;

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    var graph = builder.buildGraph(parseTree);

    var walker = new ControlFlowGraphWalker(graph);
    walker.start();

    assertThat(walker.isOnBranch()).isTrue();
    var preprocIfNode = walker.getCurrentNode();

    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("Массив=НовыйМассив");
    walker.walkNext();
    assertThat(walker.isOnBranch()).isTrue();
    var ifNode = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("ВозвратМассив");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isSameAs(graph.getExitPoint());

    walker.walkTo(ifNode);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("ВозвратПустойМассив");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isSameAs(graph.getExitPoint());

    walker.walkTo(preprocIfNode);
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    assertThat(textOfCurrentNode(walker)).isEqualTo("ВызватьИсключение\"Упс\"");
    walker.walkNext();
    assertThat(walker.getCurrentNode()).isSameAs(graph.getExitPoint());

    // Нет посторонних связей у входной ветки препроцессора
    assertThat(graph.edgesOf(preprocIfNode)).hasSize(2);
  }

  @Test
  void test_cannotAddSameEdgeTwice() {
    var graph = new ControlFlowGraph();
    var block1 = new BasicBlockVertex();
    var block2 = new BasicBlockVertex();

    graph.addVertex(block1);
    graph.addVertex(block2);

    graph.addEdge(block1, block2);
    Assertions.assertThatExceptionOfType(FlowGraphLinkException.class)
      .isThrownBy(() -> graph.addEdge(block1, block2));
  }

  @Test
  void test_cannotAddDirectEdgeToBranch() {
    var graph = new ControlFlowGraph();

    var fakeContext = mock(BSLParser.IfBranchContext.class);
    when(fakeContext.getStart()).thenReturn(new CommonToken(BSLParser.RULE_ifStatement));
    when(fakeContext.getStop()).thenReturn(new CommonToken(BSLParser.RULE_ifStatement));

    var ifBlock = new ConditionalVertex(fakeContext);
    var truePart = new BasicBlockVertex();

    graph.addVertex(ifBlock);
    graph.addVertex(truePart);

    Assertions.assertThatExceptionOfType(FlowGraphLinkException.class)
      .isThrownBy(() -> graph.addEdge(ifBlock, truePart));
  }

  @Test
  void test_preprocessorInsideIfBlockShouldNotCrash() {
    // Test for ClassCastException fix when preprocessor is inside if block
    var code = """
      Если Условие1 Тогда
          #Если Сервер Тогда
              Возврат 1;
          #Иначе
              Возврат 2;
          #КонецЕсли
      ИначеЕсли Условие2 Тогда
          Возврат 3;
      КонецЕсли;
      """;

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    
    // Should not throw ClassCastException
    var graph = builder.buildGraph(parseTree);
    
    // Verify the graph is built correctly
    assertThat(graph).isNotNull();
    assertThat(graph.vertexSet()).isNotEmpty();
    
    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(walker.isOnBranch()).isTrue();
    
    var outerIf = walker.getCurrentNode();
    assertThat(outerIf).isInstanceOf(ConditionalVertex.class);
    
    // Walk through true branch (contains preprocessor)
    walker.walkNext(CfgEdgeType.TRUE_BRANCH);
    assertThat(walker.isOnBranch()).isTrue();
    assertThat(walker.getCurrentNode()).isInstanceOf(PreprocessorConditionVertex.class);
  }

  @Test
  void test_nestedPreprocessorAndIfStatements() {
    // Test complex nesting of preprocessor and if statements
    var code = """
      #Если Сервер Тогда
          Если Условие1 Тогда
              #Если НЕ ВебКлиент Тогда
                  Если Условие2 Тогда
                      Возврат 1;
                  Иначе
                      Возврат 2;
                  КонецЕсли;
              #Иначе
                  Возврат 3;
              #КонецЕсли
          Иначе
              Возврат 4;
          КонецЕсли;
      #Иначе
          Возврат 5;
      #КонецЕсли
      """;

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    
    // Should not throw ClassCastException
    var graph = builder.buildGraph(parseTree);
    
    assertThat(graph).isNotNull();
    assertThat(graph.vertexSet()).isNotEmpty();
    
    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    
    // Should start with preprocessor condition
    assertThat(walker.isOnBranch()).isTrue();
    assertThat(walker.getCurrentNode()).isInstanceOf(PreprocessorConditionVertex.class);
  }

  @Test
  void test_preprocessorInElsifBranch() {
    // Test preprocessor inside elsif branch
    var code = """
      Если Условие1 Тогда
          Возврат 1;
      ИначеЕсли Условие2 Тогда
          #Если Сервер Тогда
              Если ВнутреннееУсловие Тогда
                  А = 1;
              КонецЕсли;
          #КонецЕсли
          Возврат 2;
      Иначе
          Возврат 3;
      КонецЕсли;
      """;

    var parseTree = parse(code);
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    
    // Should not throw ClassCastException
    var graph = builder.buildGraph(parseTree);
    
    assertThat(graph).isNotNull();
    assertThat(graph.vertexSet()).isNotEmpty();
    
    var walker = new ControlFlowGraphWalker(graph);
    walker.start();
    assertThat(walker.isOnBranch()).isTrue();
    
    var outerIf = walker.getCurrentNode();
    walker.walkNext(CfgEdgeType.FALSE_BRANCH);
    
    // Should be elsif condition
    assertThat(walker.isOnBranch()).isTrue();
    assertThat(walker.getCurrentNode()).isInstanceOf(ConditionalVertex.class);
  }

  @Test
  void test_realWorldPatternWithPreprocessorInElsifBranch() {
    // Test with real-world pattern that caused ClassCastException
    // https://github.com/1c-syntax/bsl-language-server/issues/3740
    // Extracted from НастраиваемыйОтчет/Ext/ObjectModule.bsl
    var code = """
      Процедура ПриКопировании(ОбъектКопирования)
        
        Если ТипЗнч(Основание) = Тип("ДокументСсылка.ЗаявкаНаВводДанных") Тогда
          ВидОтчета=Основание.ВидОтчета;
          
          Если НЕ ЗначениеЗаполнено(ПравилоОбработки) Тогда
            #Если Клиент Тогда
              ноПредупреждение(НСтр("ru = 'Настройки по умолчанию не определены.'"));
            #КонецЕсли
          КонецЕсли;
          
        ИначеЕсли ТипЗнч(Основание) = Тип("СправочникСсылка.ВидыОтчетов") Тогда
          ВидОтчета=Основание;
          
          Если НЕ ЗначениеЗаполнено(ПравилоОбработки) Тогда
            #Если Клиент Тогда
              ноПредупреждение(НСтр("ru = 'Настройки не определены.'"));
            #КонецЕсли
          КонецЕсли;
          
        ИначеЕсли ТипЗнч(Основание) = Тип("СправочникСсылка.ХранимыеФайлы") Тогда
          
          Если Основание.ЭтоГруппа Тогда
            #Если Клиент Тогда
              ноПредупреждение(НСтр("ru = 'Нельзя вводить на основании группы.'"));
            #КонецЕсли
            Возврат;
          КонецЕсли;
          
          Если НЕ ЗначениеЗаполнено(ПравилоОбработки) Тогда
            #Если Клиент Тогда
              ноПредупреждение(НСтр("ru = 'Настройки не определены.'"));
            #КонецЕсли
          КонецЕсли;
          
        КонецЕсли;
        
      КонецПроцедуры
      """;

    var dContext = TestUtils.getDocumentContext(code);
    var ast = dContext.getAst();
    
    assertThat(ast.subs()).isNotNull();
    var subs = ast.subs().sub();
    assertThat(subs).hasSize(1);
    
    // Build CFG - should not throw ClassCastException
    var sub = subs.get(0);
    var codeBlock = sub.procedure().subCodeBlock().codeBlock();
    
    var builder = new CfgBuildingParseTreeVisitor();
    builder.producePreprocessorConditions(true);
    
    // Should not throw ClassCastException when processing elsif branches with preprocessor directives
    var graph = builder.buildGraph(codeBlock);
    assertThat(graph).isNotNull();
    assertThat(graph.vertexSet()).isNotEmpty();
    
    // Also run diagnostics to ensure all code blocks are covered
    var diagnostics = dContext.getDiagnostics();
    assertThat(diagnostics).isNotNull();
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