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
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;

import org.jetbrains.annotations.NotNull;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class ExpressionParseTreeRewriter extends BSLParserBaseVisitor<ParseTree> {

  private final Deque<BslExpression> operands = new ArrayDeque<>();
  private final Deque<BslOperator> operatorsInFly = new ArrayDeque<>();
  
  private BslExpression resultExpression;

  public BslExpression getExpressionTree() {
    return resultExpression;
  }

  @Override
  public ParseTree visitExpression(@NotNull BSLParser.ExpressionContext ctx) {

    visitMember(ctx.member(0));
    var count = ctx.getChildCount();

    if(count > 1){
      for (int i = 1; i < count; ++i){
        var child = ctx.getChild(i);
        if(child.getClass() == BSLParser.OperationContext.class){
          visitOperation((BSLParser.OperationContext) child);
        }
        else if(child.getClass() == BSLParser.MemberContext.class){
          visitMember((BSLParser.MemberContext) child);
        }
      }
    }

    while (!operatorsInFly.isEmpty()){
      buildOperation();
    }
    resultExpression = operands.pop();

    return ctx;
  }

  @Override
  public ParseTree visitMember(BSLParser.MemberContext ctx) {

    var unary = ctx.unaryModifier();
    if(unary != null) {
      visitUnaryModifier(unary);
      ctx.getChild(1).accept(this);
      buildOperation();
      return ctx;
    }

    return super.visitMember(ctx);
  }

  @Override
  public ParseTree visitOperation(BSLParser.OperationContext ctx) {

    BslOperator operator = getOperator(ctx);

    processOperation(operator);

    return ctx;
  }

  private void processOperation(BslOperator operator){
    if(operatorsInFly.isEmpty()) {
      operatorsInFly.push(operator);
      return;
    }

    var lastSeenOperator = operatorsInFly.peek();
    if(lastSeenOperator.getPriority() <= operator.getPriority()){
      buildOperation();
    }

    operatorsInFly.push(operator);
  }

  private BslOperator getOperator(BSLParser.OperationContext ctx) {
    if(ctx.PLUS() != null){
      return BslOperator.ADD;
    }else if(ctx.MINUS() != null){
      return BslOperator.SUBTRACT;
    }
    else if(ctx.MUL() != null){
      return BslOperator.MULTIPLY;
    }
    else if(ctx.QUOTIENT() != null){
      return BslOperator.DIVIDE;
    }
    else if(ctx.MODULO() != null){
      return BslOperator.MODULO;
    }
    else if(ctx.boolOperation() != null){
      if(ctx.boolOperation().AND_KEYWORD() != null){
        return BslOperator.AND;
      }
      else{
        return BslOperator.OR;
      }
    }
    else if(ctx.compareOperation() != null){
      var token = ((TerminalNode)ctx.compareOperation().getChild(0)).getSymbol().getType();
      switch (token){
        case BSLLexer.ASSIGN:
          return BslOperator.EQUAL;
        case BSLLexer.NOT_EQUAL:
          return BslOperator.NOT_EQUAL;
        case BSLLexer.LESS:
          return BslOperator.LESS;
        case BSLLexer.LESS_OR_EQUAL:
          return BslOperator.LESS_OR_EQUAL;
        case BSLLexer.GREATER:
          return BslOperator.GREATER;
        case BSLLexer.GREATER_OR_EQUAL:
          return BslOperator.GREATER_OR_EQUAL;
      }
    }
    throw new IllegalStateException();
  }

  @Override
  public ParseTree visitConstValue(BSLParser.ConstValueContext ctx) {

    var node = TerminalSymbolNode.literal(ctx);
    operands.push(node);
    return ctx;
  }

  @Override
  public ParseTree visitUnaryModifier(BSLParser.UnaryModifierContext ctx) {
    var child = (TerminalNode)ctx.getChild(0);
    var token = (child).getSymbol().getType();

    switch (token){
      case BSLLexer.PLUS:
        operatorsInFly.push(BslOperator.UNARY_PLUS);
        break;
      case BSLLexer.MINUS:
        operatorsInFly.push(BslOperator.UNARY_MINUS);
        break;
      case BSLLexer.NOT_KEYWORD:
        operatorsInFly.push(BslOperator.NOT);
        break;
      default:
        throw new IllegalArgumentException();
    }

    return null;
  }

  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    if(ctx.IDENTIFIER() != null){
      operands.push(TerminalSymbolNode.identifier(ctx.IDENTIFIER()));
    }
    else{
      var childVariant = ctx.children.get(0);
      childVariant.accept(this);
    }

    var modifiers = ctx.modifier();
    for (var modifier: modifiers) {
      modifier.accept(this);
    }

    return ctx;
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    var name = ctx.methodName().IDENTIFIER();
    var callNode = MethodCallNode.create(name);
    callNode.setRepresentingAst(ctx);

    var paramList = ctx.doCall().callParamList();
    var parameters = paramList.callParam();
    addCallArguments(callNode, parameters);
    operands.push(callNode);
    return ctx;
  }

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    var typeName = ctx.typeName();
    var args = ctx.doCall().callParamList().callParam();
    ConstructorCallNode callNode;
    if(typeName == null){
      // function style
      var typeNameArg = args.get(0);
      args = args.stream().skip(1).collect(Collectors.toList());
      callNode = ConstructorCallNode.createDynamic(makeSubexpression(typeNameArg.expression()));
    }
    else{
      // static style
      var typeNameTerminal = TerminalSymbolNode.literal(typeName.IDENTIFIER());
      callNode = ConstructorCallNode.createStatic(typeNameTerminal);
    }

    callNode.setRepresentingAst(ctx);

    addCallArguments(callNode, args);

    operands.push(callNode);
    return ctx;
  }

  @Override
  public ParseTree visitAccessProperty(BSLParser.AccessPropertyContext ctx) {
    var target = operands.pop();
    operands.push(BinaryOperationNode.create(BslOperator.DEREFERENCE, target, TerminalSymbolNode.identifier(ctx.IDENTIFIER())));
    return ctx;
  }

  @Override
  public ParseTree visitAccessIndex(BSLParser.AccessIndexContext ctx) {
    var target = operands.pop();

    var expressionArg = makeSubexpression(ctx.expression());

    var indexOperation = BinaryOperationNode.create(BslOperator.INDEX_ACCESS, target, expressionArg);
    operands.push(indexOperation);
    return ctx;
  }

  @Override
  public ParseTree visitAccessCall(BSLParser.AccessCallContext ctx) {
    var target = operands.pop();
    var methodCall = ctx.methodCall();
    var callNode = MethodCallNode.create(methodCall.methodName().IDENTIFIER());
    addCallArguments(callNode, methodCall.doCall().callParamList().callParam());
    operands.push(BinaryOperationNode.create(BslOperator.DEREFERENCE, target, callNode));
    return ctx;
  }

  @Override
  public ParseTree visitTernaryOperator(BSLParser.TernaryOperatorContext ctx) {

    var ternary = TernaryOperatorNode.create(
      makeSubexpression(ctx.expression(0)),
      makeSubexpression(ctx.expression(1)),
      makeSubexpression(ctx.expression(2))
    );

    operands.push(ternary);

    return ctx;
  }

  private BslExpression makeSubexpression(BSLParser.ExpressionContext ctx){
    var rewriter = new ExpressionParseTreeRewriter();
    ctx.accept(rewriter);
    return rewriter.getExpressionTree();
  }

  private void addCallArguments(AbstractCallNode callNode, List<? extends BSLParser.CallParamContext> args) {
    for (BSLParser.CallParamContext parameter : args) {
      if (parameter.expression() == null) {
        callNode.addArgument(new SkippedCallArgumentNode());
      } else {
        callNode.addArgument(makeSubexpression(parameter.expression()));
      }
    }
  }

  private void buildOperation() {
    if(operatorsInFly.isEmpty()){
      return;
    }

    var operator = operatorsInFly.pop();
    switch (operator){
      case UNARY_MINUS:
      case UNARY_PLUS:
      case NOT:
        var operand = operands.pop();
        var operation = UnaryOperationNode.Create(operator, operand);
        operands.push(operation);
        break;
      default:
        var right = operands.pop();
        var left = operands.pop();
        var binaryOp = BinaryOperationNode.create(operator, left, right);
        operands.push(binaryOp);
    }
  }
}
