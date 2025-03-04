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
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.StringInterner;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.Tree;
import org.eclipse.lsp4j.Range;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

// idea from https://pdepend.org/documentation/software-metrics/cyclomatic-complexity.html
@Component
@Scope(SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class CyclomaticComplexityComputer
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

  @PostConstruct
  public void init() {
    fileComplexity = 0;
    fileCodeBlockComplexity = 0;
    fileBlockComplexitySecondaryLocations = new ArrayList<>();
    resetMethodComplexityCounters();
    methodsComplexity = new HashMap<>();
    methodsComplexitySecondaryLocations = new HashMap<>();
  }

  @Override
  public ComplexityData compute() {
    fileComplexity = 0;
    fileCodeBlockComplexity = 0;
    resetMethodComplexityCounters();
    methodsComplexity.clear();

    ParseTreeWalker walker = new ParseTreeWalker();
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
    complexityIncrement(currentMethod.getSubNameRange());

    super.enterSub(ctx);
  }

  @Override
  public void exitSub(BSLParser.SubContext ctx) {
    incrementFileComplexity();
    if (currentMethod != null) {
      methodsComplexity.put(currentMethod, complexity);
    }
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
    incrementFileCodeBlockComplexity();
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
    super.exitFileCodeBlock(ctx);
  }

  @Override
  public void enterIfBranch(BSLParser.IfBranchContext ctx) {
    complexityIncrement(ctx.IF_KEYWORD().getSymbol());
    super.enterIfBranch(ctx);
  }

  @Override
  public void enterElsifBranch(BSLParser.ElsifBranchContext ctx) {
    complexityIncrement(ctx.ELSIF_KEYWORD().getSymbol());
    super.enterElsifBranch(ctx);
  }

  @Override
  public void enterElseBranch(BSLParser.ElseBranchContext ctx) {
    complexityIncrement(ctx.ELSE_KEYWORD().getSymbol());
    super.enterElseBranch(ctx);
  }

  @Override
  public void enterTernaryOperator(BSLParser.TernaryOperatorContext ctx) {
    complexityIncrement(ctx.QUESTION().getSymbol());
    super.enterTernaryOperator(ctx);
  }

  @Override
  public void enterForEachStatement(BSLParser.ForEachStatementContext ctx) {
    complexityIncrement(ctx.FOR_KEYWORD().getSymbol());
    super.enterForEachStatement(ctx);
  }

  @Override
  public void enterForStatement(BSLParser.ForStatementContext ctx) {
    complexityIncrement(ctx.FOR_KEYWORD().getSymbol());
    super.enterForStatement(ctx);
  }

  @Override
  public void enterWhileStatement(BSLParser.WhileStatementContext ctx) {
    complexityIncrement(ctx.WHILE_KEYWORD().getSymbol());
    super.enterWhileStatement(ctx);
  }

  @Override
  public void enterExceptCodeBlock(BSLParser.ExceptCodeBlockContext ctx) {
    complexityIncrement(((BSLParser.TryStatementContext) ctx.getParent()).EXCEPT_KEYWORD().getSymbol());
    super.enterExceptCodeBlock(ctx);
  }

  @Override
  public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    var methodNameContext = ctx.methodName();
    if (methodNameContext != null && currentMethod != null) {
      String calledMethodName = methodNameContext.getText();
      if (currentMethod.getName().equalsIgnoreCase(calledMethodName)) {
        complexityIncrement(methodNameContext.IDENTIFIER().getSymbol());
      }
    }

    super.enterGlobalMethodCall(ctx);
  }

  @Override
  public void enterGotoStatement(BSLParser.GotoStatementContext ctx) {
    complexityIncrement(ctx.GOTO_KEYWORD().getSymbol());
    super.enterGotoStatement(ctx);
  }

  @Override
  public void enterExpression(BSLParser.ExpressionContext ctx) {

    int emptyTokenType = -1;
    flattenExpression(ctx).stream()
      .filter((Token token) -> token.getType() != emptyTokenType)
      .forEach(this::complexityIncrement);
    super.enterExpression(ctx);
  }

  private static List<Token> flattenExpression(BSLParser.ExpressionContext ctx) {

    List<Token> result = new ArrayList<>();

    final List<Tree> children = Trees.getChildren(ctx);
    for (Tree tree : children) {
      if (!(tree instanceof BSLParserRuleContext parserRule)) {
        continue;
      }

      if (parserRule instanceof BSLParser.MemberContext memberContext) {
        flattenMember(result, memberContext);
      } else if (parserRule instanceof BSLParser.OperationContext operationContext) {
        flattenOperation(result, operationContext);
      }
    }

    return result;
  }

  private static void flattenMember(List<Token> result, BSLParser.MemberContext member) {
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
  }

  private void incrementFileComplexity() {
    fileComplexity += complexity;
  }

  private void incrementFileCodeBlockComplexity() {
    fileCodeBlockComplexity += complexity;
  }

  private void complexityIncrement(Token token) {
    complexityIncrement(Ranges.create(token));
  }

  private void complexityIncrement(Range range) {
    complexity += 1;
    addSecondaryLocation(range);
  }

  private void addSecondaryLocation(Range range) {
    String message;
    message = String.format("+%d", 1);
    var secondaryLocation = new ComplexitySecondaryLocation(range, stringInterner.intern(message));
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
