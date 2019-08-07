/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.context.computer;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserBaseListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

// Based on Cognitive Complexity white-paper, version 1.4.
// See https://www.sonarsource.com/docs/CognitiveComplexity.pdf for details.
public class CognitiveComplexityComputer
  extends BSLParserBaseListener
  implements Computer<CognitiveComplexityComputer.Result> {

  private final DocumentContext documentContext;

  private int fileComplexity;
  private Map<MethodSymbol, Integer> methodsComplexity;

  private MethodSymbol currentMethod;
  private int complexity;
  private int nestedLevel;

  public CognitiveComplexityComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
    fileComplexity = 0;
    resetMethodComplexityCounters();
    methodsComplexity = new HashMap<>();
  }

  @Override
  public Result compute() {
    fileComplexity = 0;
    resetMethodComplexityCounters();
    methodsComplexity.clear();

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(this, documentContext.getAst());

    return new Result(fileComplexity, methodsComplexity);
  }

  @Override
  public void enterSub(BSLParser.SubContext ctx) {
    Optional<MethodSymbol> methodSymbol = documentContext.getMethodSymbol(ctx);
    if (!methodSymbol.isPresent()) {
      return;
    }

    resetMethodComplexityCounters();
    currentMethod = methodSymbol.get();

    super.enterSub(ctx);
  }

  @Override
  public void exitSub(BSLParser.SubContext ctx) {
    incrementFileComplexity();
    methodsComplexity.put(currentMethod, complexity);
    currentMethod = null;
    super.exitSub(ctx);
  }

  @Override
  public void enterFileCodeBlockBeforeSub(BSLParser.FileCodeBlockBeforeSubContext ctx) {
    resetMethodComplexityCounters();
    super.enterFileCodeBlockBeforeSub(ctx);
  }

  @Override
  public void exitFileCodeBlockBeforeSub(BSLParser.FileCodeBlockBeforeSubContext ctx) {
    incrementFileComplexity();
    super.exitFileCodeBlockBeforeSub(ctx);
  }

  @Override
  public void enterFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    resetMethodComplexityCounters();
    super.enterFileCodeBlock(ctx);
  }

  @Override
  public void exitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    incrementFileComplexity();
    super.exitFileCodeBlock(ctx);
  }

  @Override
  public void enterIfBranch(BSLParser.IfBranchContext ctx) {
    structuralIncrement();
    super.enterIfBranch(ctx);
  }

  @Override
  public void exitIfBranch(BSLParser.IfBranchContext ctx) {
    nestedLevel--;
    super.exitIfBranch(ctx);
  }

  @Override
  public void enterElsifBranch(BSLParser.ElsifBranchContext ctx) {
    hybridIncrement();
    super.enterElsifBranch(ctx);
  }

  @Override
  public void exitElsifBranch(BSLParser.ElsifBranchContext ctx) {
    nestedLevel--;
    super.exitElsifBranch(ctx);
  }

  @Override
  public void enterElseBranch(BSLParser.ElseBranchContext ctx) {
    hybridIncrement();
    super.enterElseBranch(ctx);
  }

  @Override
  public void exitElseBranch(BSLParser.ElseBranchContext ctx) {
    nestedLevel--;
    super.exitElseBranch(ctx);
  }

  @Override
  public void enterTernaryOperator(BSLParser.TernaryOperatorContext ctx) {
    structuralIncrement();
    super.enterTernaryOperator(ctx);
  }

  @Override
  public void exitTernaryOperator(BSLParser.TernaryOperatorContext ctx) {
    nestedLevel--;
    super.exitTernaryOperator(ctx);
  }

  @Override
  public void enterForEachStatement(BSLParser.ForEachStatementContext ctx) {
    structuralIncrement();
    super.enterForEachStatement(ctx);
  }

  @Override
  public void exitForEachStatement(BSLParser.ForEachStatementContext ctx) {
    nestedLevel--;
    super.exitForEachStatement(ctx);
  }

  @Override
  public void enterForStatement(BSLParser.ForStatementContext ctx) {
    structuralIncrement();
    super.enterForStatement(ctx);
  }

  @Override
  public void exitForStatement(BSLParser.ForStatementContext ctx) {
    nestedLevel--;
    super.exitForStatement(ctx);
  }

  @Override
  public void enterWhileStatement(BSLParser.WhileStatementContext ctx) {
    structuralIncrement();
    super.enterWhileStatement(ctx);
  }

  @Override
  public void exitWhileStatement(BSLParser.WhileStatementContext ctx) {
    nestedLevel--;
    super.exitWhileStatement(ctx);
  }

  @Override
  public void enterExceptCodeBlock(BSLParser.ExceptCodeBlockContext ctx) {
    structuralIncrement();
    super.enterExceptCodeBlock(ctx);
  }

  @Override
  public void exitExceptCodeBlock(BSLParser.ExceptCodeBlockContext ctx) {
    nestedLevel--;
    super.exitExceptCodeBlock(ctx);
  }

  @Override
  public void enterGotoStatement(BSLParser.GotoStatementContext ctx) {
    fundamentalIncrement();
    super.enterGotoStatement(ctx);
  }

  @Override
  public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    BSLParser.MethodNameContext methodNameContext = ctx.methodName();
    if (methodNameContext != null && currentMethod != null) {
      String calledMethodName = methodNameContext.getText();
      if (currentMethod.getName().equalsIgnoreCase(calledMethodName)) {
        fundamentalIncrement();
      }
    }

    super.enterGlobalMethodCall(ctx);
  }

  @Override
  public void enterExpression(BSLParser.ExpressionContext ctx) {
    List<BSLParser.OperationContext> operations = ctx.operation();
    if (!operations.isEmpty()) {
      AtomicInteger lastOperationType = new AtomicInteger(operations.get(0).getStart().getType());

      operations.forEach((BSLParser.OperationContext operationContext) -> {
        int currentOperationType = operationContext.getStart().getType();
        if (lastOperationType.get() != currentOperationType) {
          fundamentalIncrement();
          lastOperationType.set(currentOperationType);
        }
      });
    }

    super.enterExpression(ctx);
  }

  private void resetMethodComplexityCounters() {
    complexity = 0;
    nestedLevel = 0;
  }

  private void incrementFileComplexity() {
    fileComplexity += complexity;
  }

  private void structuralIncrement() {
    complexity += 1 + nestedLevel;
    nestedLevel++;
  }

  private void fundamentalIncrement() {
    complexity++;
  }

  private void hybridIncrement() {
    complexity++;
    nestedLevel++;
  }

  @Value
  @AllArgsConstructor
  public static class Result {
    private final int fileComplexity;
    private final Map<MethodSymbol, Integer> methodsComplexity;
  }
}
