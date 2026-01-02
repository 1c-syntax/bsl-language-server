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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseListener;
import com.github._1c_syntax.utils.StringInterner;
import io.sentry.spring.jakarta.tracing.SentrySpan;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.Tree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

// Based on Cognitive Complexity white-paper, version 1.4.
// See https://www.sonarsource.com/docs/CognitiveComplexity.pdf for details.

/**
 * Вычислитель когнитивной сложности кода.
 * <p>
 * Вычисляет метрику когнитивной сложности (Cognitive Complexity)
 * для файла и каждого метода с указанием вторичных локаций.
 * Основан на методике SonarSource.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class CognitiveComplexityComputer
  extends BSLParserBaseListener
  implements Computer<ComplexityData> {

  private final DocumentContext documentContext;

  @Setter(onMethod = @__({@Autowired}), value = AccessLevel.PACKAGE)
  private StringInterner stringInterner;

  private int fileComplexity;
  private int fileCodeBlockComplexity;
  private List<ComplexitySecondaryLocation> fileBlockComplexitySecondaryLocations;

  private Map<MethodSymbol, Integer> methodsComplexity;
  private Map<MethodSymbol, List<ComplexitySecondaryLocation>> methodsComplexitySecondaryLocations;

  private MethodSymbol currentMethod;
  private int complexity;
  private int nestedLevel;
  private Set<ParserRuleContext> ignoredContexts;

  @PostConstruct
  public void init() {
    fileComplexity = 0;
    fileCodeBlockComplexity = 0;
    fileBlockComplexitySecondaryLocations = new ArrayList<>();
    resetMethodComplexityCounters();
    methodsComplexity = new HashMap<>();
    methodsComplexitySecondaryLocations = new HashMap<>();
    ignoredContexts = new HashSet<>();
  }

  @Override
  @SentrySpan
  public ComplexityData compute() {
    fileComplexity = 0;
    fileCodeBlockComplexity = 0;
    resetMethodComplexityCounters();
    methodsComplexity.clear();
    ignoredContexts.clear();

    var walker = new ParseTreeWalker();
    walker.walk(this, documentContext.getAst());

    return new ComplexityData(
      fileComplexity,
      fileCodeBlockComplexity,
      fileBlockComplexitySecondaryLocations,
      methodsComplexity,
      methodsComplexitySecondaryLocations
    );
  }

  @Override
  public void enterSub(BSLParser.SubContext ctx) {
    Optional<MethodSymbol> methodSymbol = documentContext.getSymbolTree().getMethodSymbol(ctx);
    if (methodSymbol.isEmpty()) {
      return;
    }

    resetMethodComplexityCounters();
    currentMethod = methodSymbol.get();

    super.enterSub(ctx);
  }

  @Override
  public void exitSub(BSLParser.SubContext ctx) {
    incrementFileComplexity();
    if (currentMethod != null) {
      methodsComplexity.put(currentMethod, complexity);
    }
    currentMethod = null;
    ignoredContexts.clear();
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
    incrementFileCodeBlockComplexity();
    ignoredContexts.clear();
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
    incrementFileCodeBlockComplexity();
    ignoredContexts.clear();
    super.exitFileCodeBlock(ctx);
  }

  @Override
  public void enterIfBranch(BSLParser.IfBranchContext ctx) {
    structuralIncrement(ctx.IF_KEYWORD().getSymbol());
    super.enterIfBranch(ctx);
  }

  @Override
  public void exitIfBranch(BSLParser.IfBranchContext ctx) {
    nestedLevel--;
    super.exitIfBranch(ctx);
  }

  @Override
  public void enterElsifBranch(BSLParser.ElsifBranchContext ctx) {
    hybridIncrement(ctx.ELSIF_KEYWORD().getSymbol());
    super.enterElsifBranch(ctx);
  }

  @Override
  public void exitElsifBranch(BSLParser.ElsifBranchContext ctx) {
    nestedLevel--;
    super.exitElsifBranch(ctx);
  }

  @Override
  public void enterElseBranch(BSLParser.ElseBranchContext ctx) {
    hybridIncrement(ctx.ELSE_KEYWORD().getSymbol());
    super.enterElseBranch(ctx);
  }

  @Override
  public void exitElseBranch(BSLParser.ElseBranchContext ctx) {
    nestedLevel--;
    super.exitElseBranch(ctx);
  }

  @Override
  public void enterTernaryOperator(BSLParser.TernaryOperatorContext ctx) {
    structuralIncrement(ctx.QUESTION().getSymbol());
    super.enterTernaryOperator(ctx);
  }

  @Override
  public void exitTernaryOperator(BSLParser.TernaryOperatorContext ctx) {
    nestedLevel--;
    super.exitTernaryOperator(ctx);
  }

  @Override
  public void enterForEachStatement(BSLParser.ForEachStatementContext ctx) {
    structuralIncrement(ctx.FOR_KEYWORD().getSymbol());
    super.enterForEachStatement(ctx);
  }

  @Override
  public void exitForEachStatement(BSLParser.ForEachStatementContext ctx) {
    nestedLevel--;
    super.exitForEachStatement(ctx);
  }

  @Override
  public void enterForStatement(BSLParser.ForStatementContext ctx) {
    structuralIncrement(ctx.FOR_KEYWORD().getSymbol());
    super.enterForStatement(ctx);
  }

  @Override
  public void exitForStatement(BSLParser.ForStatementContext ctx) {
    nestedLevel--;
    super.exitForStatement(ctx);
  }

  @Override
  public void enterWhileStatement(BSLParser.WhileStatementContext ctx) {
    structuralIncrement(ctx.WHILE_KEYWORD().getSymbol());
    super.enterWhileStatement(ctx);
  }

  @Override
  public void exitWhileStatement(BSLParser.WhileStatementContext ctx) {
    nestedLevel--;
    super.exitWhileStatement(ctx);
  }

  @Override
  public void enterExceptCodeBlock(BSLParser.ExceptCodeBlockContext ctx) {
    structuralIncrement(((BSLParser.TryStatementContext) ctx.getParent()).EXCEPT_KEYWORD().getSymbol());
    super.enterExceptCodeBlock(ctx);
  }

  @Override
  public void exitExceptCodeBlock(BSLParser.ExceptCodeBlockContext ctx) {
    nestedLevel--;
    super.exitExceptCodeBlock(ctx);
  }

  @Override
  public void enterGotoStatement(BSLParser.GotoStatementContext ctx) {
    fundamentalIncrement(ctx.GOTO_KEYWORD().getSymbol());
    super.enterGotoStatement(ctx);
  }

  @Override
  public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    var methodNameContext = ctx.methodName();
    if (methodNameContext != null && currentMethod != null) {
      String calledMethodName = methodNameContext.getText();
      if (currentMethod.getName().equalsIgnoreCase(calledMethodName)) {
        fundamentalIncrement(methodNameContext.IDENTIFIER().getSymbol());
      }
    }

    super.enterGlobalMethodCall(ctx);
  }

  @Override
  public void enterExpression(BSLParser.ExpressionContext ctx) {

    if (ignoredContexts.contains(ctx)) {
      return;
    }

    final List<Token> flattenExpression = flattenExpression(ctx);

    int emptyTokenType = -1;
    var lastOperationType = new AtomicInteger(emptyTokenType);

    flattenExpression.forEach((Token token) -> {
      int currentOperationType = token.getType();
      if (lastOperationType.get() != currentOperationType) {
        lastOperationType.set(currentOperationType);
        if (currentOperationType != emptyTokenType) {
          fundamentalIncrement(token);
        }
      }
    });

    super.enterExpression(ctx);
  }

  private List<Token> flattenExpression(BSLParser.ExpressionContext ctx) {

    ignoredContexts.add(ctx);

    List<Token> result = new ArrayList<>();

    final List<Tree> children = Trees.getChildren(ctx);
    for (Tree tree : children) {
      if (tree instanceof BSLParser.MemberContext memberContext) {
        flattenMember(result, memberContext);
      } else if (tree instanceof BSLParser.OperationContext operationContext) {
        flattenOperation(result, operationContext);
      } else {
        // no-op
      }
    }

    return result;
  }

  private void flattenMember(List<Token> result, BSLParser.MemberContext member) {
    final BSLParser.ExpressionContext expression = member.expression();

    if (expression == null) {
      return;
    }

    final List<Token> nestedTokens = flattenExpression(expression);
    if (nestedTokens.isEmpty()) {
      return;
    }

    final BSLParser.UnaryModifierContext unaryModifier = member.unaryModifier();

    if (unaryModifier != null && unaryModifier.NOT_KEYWORD() != null) {
      final var splitter = new CommonToken(-1);
      result.add(splitter);
      result.addAll(nestedTokens);
      result.add(splitter);
    } else {
      result.addAll(nestedTokens);
    }
  }

  private static void flattenOperation(List<Token> result, BSLParser.OperationContext operation) {
    final BSLParser.BoolOperationContext boolOperation = operation.boolOperation();

    if (boolOperation != null) {
      result.add(boolOperation.getStart());
    }
  }

  private void resetMethodComplexityCounters() {
    complexity = 0;
    nestedLevel = 0;
  }

  private void incrementFileComplexity() {
    fileComplexity += complexity;
  }

  private void incrementFileCodeBlockComplexity() {
    fileCodeBlockComplexity += complexity;
  }

  private void structuralIncrement(Token token) {
    complexity += 1 + nestedLevel;
    addSecondaryLocation(token, 1 + nestedLevel, nestedLevel);
    nestedLevel++;
  }

  private void fundamentalIncrement(Token token) {
    complexity++;
    addSecondaryLocation(token);
  }

  private void hybridIncrement(Token token) {
    complexity++;
    nestedLevel++;
    addSecondaryLocation(token);
  }

  private void addSecondaryLocation(Token token) {
    addSecondaryLocation(token, 1, 0);
  }

  private void addSecondaryLocation(Token token, int increment, int nested) {
    String message;
    if (nested > 0) {
      message = String.format("+%d (nesting = %d)", increment, nested);
    } else {
      message = String.format("+%d", increment);
    }
    var secondaryLocation = new ComplexitySecondaryLocation(Ranges.create(token), stringInterner.intern(message));
    List<ComplexitySecondaryLocation> locations;
    if (currentMethod != null) {
      locations = methodsComplexitySecondaryLocations.computeIfAbsent(
        currentMethod,
        (MethodSymbol methodSymbol) -> new ArrayList<>()
      );
    } else {
      locations = fileBlockComplexitySecondaryLocations;
    }

    locations.add(secondaryLocation);
  }
}
