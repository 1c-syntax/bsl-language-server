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

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CfgBuildingParseTreeVisitor extends BSLParserBaseVisitor<ParseTree> {

  private StatementsBlockWriter blocks;
  private ControlFlowGraph graph;
  private Map<String, LabelVertex> jumpLabels;

  private boolean produceLoopIterationsEnabled = true;
  private boolean producePreprocessorConditionsEnabled = true;
  private boolean adjacentDeadCodeEnabled = false;

  public void produceLoopIterations(boolean enable) {
    produceLoopIterationsEnabled = enable;
  }

  public void producePreprocessorConditions(boolean enable) {
    producePreprocessorConditionsEnabled = enable;
  }

  public void determineAdjacentDeadCode(boolean enabled) {
    adjacentDeadCodeEnabled = enabled;
  }

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

    var conditionStatement = new ConditionalVertex(ctx.ifBranch());
    graph.addVertex(conditionStatement);

    connectGraphTail(blocks.getCurrentBlock(), conditionStatement);

    // подграф if
    blocks.enterBlock();
    var currentLevelBlock = blocks.getCurrentBlock();

    // тело true
    blocks.enterBlock();
    if (ctx.ifBranch().codeBlock() != null) {
      ctx.ifBranch().codeBlock().accept(this);
    }
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
      // это мертвый код. Он может быть пустым блоком
      // тогда он не нужен сам по себе
      if (hasNoSignificantEdges(blockTail)
        && blockTail instanceof BasicBlockVertex basicBlock
        && basicBlock.statements().isEmpty()) {
        continue;
      }
      graph.addEdge(blockTail, upperBlock.end());
    }

    return ctx;
  }

  private boolean hasNoSignificantEdges(CfgVertex blockTail) {
    var edges = graph.incomingEdgesOf(blockTail);
    return edges.isEmpty()
      || (adjacentDeadCodeEnabled
      && edges.stream().allMatch(x -> x.getType() == CfgEdgeType.ADJACENT_CODE));
  }

  @Override
  public ParseTree visitElsifBranch(BSLParser.ElsifBranchContext ctx) {

    var previousCondition = blocks.getCurrentBlock().getBuildParts().pop();

    var condition = new ConditionalVertex(ctx);
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
    var loopStart = new WhileLoopVertex(ctx);
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
  public ParseTree visitGotoStatement(BSLParser.GotoStatementContext ctx) {

    blocks.addStatement(ctx);

    var name = ctx.labelName().getText();
    var labelVertex = createOrGetKnownLabel(name);

    var block = blocks.getCurrentBlock();
    var currentTail = blocks.getCurrentBlock().end();
    connectGraphTail(block, labelVertex);

    // создадим новый end, в который будут помещаться все будущие строки
    block.split();
    graph.addVertex(block.end());
    connectAdjacentCode(currentTail);

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
    blocks.addStatement(ctx);
    var currentTail = blocks.getCurrentBlock().end();
    var jumps = blocks.getCurrentBlock().getJumpContext();
    makeJump(jumps.loopContinue);
    connectAdjacentCode(currentTail);
    return ctx;
  }

  @Override
  public ParseTree visitReturnStatement(BSLParser.ReturnStatementContext ctx) {
    blocks.addStatement(ctx);
    var currentTail = blocks.getCurrentBlock().end();
    var jumps = blocks.getCurrentBlock().getJumpContext();
    makeJump(jumps.methodReturn);
    connectAdjacentCode(currentTail);
    return ctx;
  }

  @Override
  public ParseTree visitBreakStatement(BSLParser.BreakStatementContext ctx) {
    blocks.addStatement(ctx);
    var currentTail = blocks.getCurrentBlock().end();
    var jumps = blocks.getCurrentBlock().getJumpContext();
    makeJump(jumps.loopBreak);
    connectAdjacentCode(currentTail);
    return ctx;
  }

  @Override
  public ParseTree visitTryStatement(BSLParser.TryStatementContext ctx) {

    var tryBranch = new TryExceptVertex(ctx);
    graph.addVertex(tryBranch);
    connectGraphTail(blocks.getCurrentBlock(), tryBranch);

    // весь блок try
    blocks.enterBlock();

    // Handle exceptCodeBlock - check for null before accessing
    StatementsBlockWriter.StatementsBlockRecord exception;
    if (ctx.exceptCodeBlock() != null) {
      blocks.enterBlock();
      ctx.exceptCodeBlock().accept(this);
      exception = blocks.leaveBlock();
    } else {
      // Handle the case when exceptCodeBlock is null
      var dummyExceptionVertex = new BasicBlockVertex();
      graph.addVertex(dummyExceptionVertex);
      
      // Create a new block and manually set its begin and end vertices
      var jumpInfo = new StatementsBlockWriter.JumpInformationRecord();
      blocks.enterBlock(jumpInfo);
      var dummyBlock = blocks.getCurrentBlock();
      blocks.leaveBlock();
      
      // Now use the dummy block as our exception block
      exception = dummyBlock;
      exception.replaceEnd(dummyExceptionVertex);
    }

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
    var currentTail = blocks.getCurrentBlock().end();
    var jumps = blocks.getCurrentBlock().getJumpContext();
    makeJump(jumps.exceptionHandler);
    connectAdjacentCode(currentTail);
    return ctx;
  }

  @Override
  public ParseTree visitPreproc_if(BSLParser.Preproc_ifContext ctx) {

    if (!producePreprocessorConditionsEnabled) {
      return ctx;
    }

    if (!isStatementLevelPreproc(ctx)) {
      return super.visitPreproc_if(ctx);
    }

    var node = new PreprocessorConditionVertex(ctx);
    graph.addVertex(node);
    connectGraphTail(blocks.getCurrentBlock(), node);

    var mainIf = blocks.enterBlock();
    var body = blocks.enterBlock(); // тело идущего следом блока

    graph.addVertex(body.begin());
    graph.addEdge(node, body.begin(), CfgEdgeType.TRUE_BRANCH);

    body.getBuildParts().push(node);

    mainIf.getBuildParts().push(body.begin());

    return super.visitPreproc_if(ctx);
  }

  @Override
  public ParseTree visitPreproc_else(BSLParser.Preproc_elseContext ctx) {

    if (!producePreprocessorConditionsEnabled) {
      return ctx;
    }

    // По грамматике это может быть оторванный препроцессор, без начала
    var condition = popPreprocCondition();
    if (condition == null) {
      return super.visitPreproc_else(ctx);
    }

    var previousBody = blocks.leaveBlock();
    blocks.getCurrentBlock().getBuildParts().push(previousBody.end());

    var elseBody = blocks.enterBlock();
    graph.addVertex(elseBody.begin());
    graph.addEdge(condition, elseBody.begin(), CfgEdgeType.FALSE_BRANCH);

    elseBody.getBuildParts().push(condition); // маркер состояния обработки препроцессора

    return ctx;

  }

  @Override
  public ParseTree visitPreproc_elsif(BSLParser.Preproc_elsifContext ctx) {

    if (!producePreprocessorConditionsEnabled) {
      return ctx;
    }

    // По грамматике это может быть оторванный препроцессор, без начала
    var condition = popPreprocCondition();
    if (condition == null) {
      return super.visitPreproc_elsif(ctx);
    }

    var newCondition = new PreprocessorConditionVertex(ctx);
    graph.addVertex(newCondition);
    graph.addEdge(condition, newCondition, CfgEdgeType.FALSE_BRANCH);

    var previousBody = blocks.leaveBlock();
    blocks.getCurrentBlock().getBuildParts().push(previousBody.end());

    var body = blocks.enterBlock();
    graph.addVertex(body.begin());
    graph.addEdge(newCondition, body.begin(), CfgEdgeType.TRUE_BRANCH);

    body.getBuildParts().push(newCondition);

    return ctx;

  }

  @Override
  public ParseTree visitPreproc_endif(BSLParser.Preproc_endifContext ctx) {

    if (!producePreprocessorConditionsEnabled) {
      return ctx;
    }

    // проверка маркера
    var condition = popPreprocCondition();
    if (condition == null) {
      return super.visitPreproc_endif(ctx);
    }

    var previousBody = blocks.leaveBlock();
    var mainIf = blocks.leaveBlock();
    mainIf.getBuildParts().push(previousBody.end());

    var upperBlock = blocks.getCurrentBlock();
    upperBlock.split();
    graph.addVertex(upperBlock.end());

    // если блоки альтернатив были, то у условия будет уже 2 выхода
    if (graph.outgoingEdgesOf(condition).size() < 2) {
      graph.addEdge(condition, upperBlock.end(), CfgEdgeType.FALSE_BRANCH);
    }

    // присоединяем все прямые выходы из тел условий
    while (!mainIf.getBuildParts().isEmpty()) {
      var blockTail = mainIf.getBuildParts().pop();

      // это мертвый код. Он может быть пустым блоком
      // тогда он не нужен сам по себе
      if (hasNoSignificantEdges(blockTail)
        && blockTail instanceof BasicBlockVertex basicBlock
        && basicBlock.statements().isEmpty()) {

        graph.removeVertex(basicBlock);
        continue;
      }
      graph.addVertex(blockTail);
      graph.addEdge(blockTail, upperBlock.end());
    }

    return ctx;
  }

  private static boolean isStatementLevelPreproc(BSLParserRuleContext ctx) {
    return ctx.getParent().getParent().getRuleIndex() == BSLParser.RULE_statement;
  }

  private PreprocessorConditionVertex popPreprocCondition() {
    var node = blocks.getCurrentBlock().getBuildParts().peek();
    if (node instanceof PreprocessorConditionVertex preprocessorConditionVertex) {
      blocks.getCurrentBlock().getBuildParts().pop();
      return preprocessorConditionVertex;
    }
    return null;
  }

  private void connectAdjacentCode(CfgVertex currentTail) {
    if (adjacentDeadCodeEnabled) {
      var newTail = blocks.getCurrentBlock().end();
      graph.addEdge(currentTail, newTail, CfgEdgeType.ADJACENT_CODE);
    }
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
    if (produceLoopIterationsEnabled) {
      graph.addEdge(body.end(), loopStart, CfgEdgeType.LOOP_ITERATION);
    }
  }

  private void connectGraphTail(StatementsBlockWriter.StatementsBlockRecord currentBlock, CfgVertex vertex) {

    if (!(currentBlock.end() instanceof BasicBlockVertex currentTail)) {
      graph.addEdge(currentBlock.end(), vertex);
      return;
    }

    if (currentTail.statements().isEmpty()) {
      // перевести все связи на новую вершину
      var incoming = graph.incomingEdgesOf(currentTail);
      for (var edge : incoming) {
        // ребра смежности не переключаем, т.к. текущий блок удаляется
        if (edge.getType() == CfgEdgeType.ADJACENT_CODE) {
          continue;
        }

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
      .filter(vertex -> !(vertex instanceof ExitVertex))
      .filter((CfgVertex vertex) -> {
        var edges = new ArrayList<>(graph.edgesOf(vertex));

        return edges.isEmpty()
          || (adjacentDeadCodeEnabled
          && edges.size() == 1
          && edges.get(0).getType() == CfgEdgeType.ADJACENT_CODE
          && graph.getEdgeTarget(edges.get(0)) == vertex);

      })
      .toList();

    // в одном стриме бывает ConcurrentModificationException
    // делаем через другую коллекцию
    orphans.forEach(x -> graph.removeVertex(x));
  }

  private LabelVertex createOrGetKnownLabel(String labelName) {
    // Don't trust Sonar. Предложение заменить на computeIfAbsent
    // вызывает ConcurrentModificationException

    var labelVertex = jumpLabels.get(labelName);
    if (labelVertex == null) {
      labelVertex = new LabelVertex(labelName);
      jumpLabels.put(labelName, labelVertex);
      graph.addVertex(labelVertex);
    }

    return labelVertex;
  }
}
