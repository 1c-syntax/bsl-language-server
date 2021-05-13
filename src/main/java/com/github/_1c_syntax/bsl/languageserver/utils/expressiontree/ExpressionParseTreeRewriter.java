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
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class ExpressionParseTreeRewriter extends BSLParserBaseVisitor<ParseTree> {

  @Value
  private static class OperatorInCode {
    BslOperator operator;
    String actualSourceCode; // ИЛИ vs OR в диагностических сообщениях, как написано в коде

    public int getPriority() {
      return operator.getPriority();
    }
  }

  private final Deque<BslExpression> operands = new ArrayDeque<>();
  private final Deque<OperatorInCode> operatorsInFly = new ArrayDeque<>();

  private BslExpression resultExpression;
  private int recursionLevel = -1;

  public BslExpression getExpressionTree() {
    return resultExpression;
  }

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {

    var nestingCount = operatorsInFly.size();
    recursionLevel++;

    visitMember(ctx.member(0));
    var count = ctx.getChildCount();

    if (count > 1) {
      for (var i = 1; i < count; ++i) {
        var child = ctx.getChild(i);
        if (child.getClass() == BSLParser.OperationContext.class) {
          visitOperation((BSLParser.OperationContext) child);
        } else if (child.getClass() == BSLParser.MemberContext.class) {
          visitMember((BSLParser.MemberContext) child);
        }
      }
    }

    var addToOperands = recursionLevel > 0;

    while (nestingCount < operatorsInFly.size()) {
      buildOperation();
    }

    var operation = operands.peek();
    assert operation != null; // для спокойствия сонара
    
    if(operation.getRepresentingAst() == null)
      operation.setRepresentingAst(ctx);

    if (!addToOperands) {
      resultExpression = operands.pop();
    }

    recursionLevel--;
    return ctx;
  }

  @Override
  public ParseTree visitMember(BSLParser.MemberContext ctx) {

    var unary = ctx.unaryModifier();
    if (unary != null) {
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

    processOperation(new OperatorInCode(operator, ctx.getText()));

    return ctx;
  }

  private void processOperation(OperatorInCode operator) {
    if (operatorsInFly.isEmpty()) {
      operatorsInFly.push(operator);
      return;
    }

    var lastSeenOperator = operatorsInFly.peek();
    if (lastSeenOperator.getPriority() <= operator.getPriority()) {
      buildOperation();
    }

    operatorsInFly.push(operator);
  }

  private BslOperator getOperator(BSLParser.OperationContext ctx) {
    if (ctx.PLUS() != null) {
      return BslOperator.ADD;
    } else if (ctx.MINUS() != null) {
      return BslOperator.SUBTRACT;
    } else if (ctx.MUL() != null) {
      return BslOperator.MULTIPLY;
    } else if (ctx.QUOTIENT() != null) {
      return BslOperator.DIVIDE;
    } else if (ctx.MODULO() != null) {
      return BslOperator.MODULO;
    } else if (ctx.boolOperation() != null) {
      if (ctx.boolOperation().AND_KEYWORD() != null) {
        return BslOperator.AND;
      } else {
        return BslOperator.OR;
      }
    } else if (ctx.compareOperation() != null) {
      var token = ((TerminalNode) ctx.compareOperation().getChild(0)).getSymbol().getType();
      switch (token) {
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
        default:
          break;
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
    var child = (TerminalNode) ctx.getChild(0);
    var token = (child).getSymbol().getType();

    BslOperator operator;

    switch (token) {
      case BSLLexer.PLUS:
        operator = BslOperator.UNARY_PLUS;
        break;
      case BSLLexer.MINUS:
        operator = BslOperator.UNARY_MINUS;
        break;
      case BSLLexer.NOT_KEYWORD:
        operator = BslOperator.NOT;
        break;
      default:
        throw new IllegalArgumentException();
    }

    operatorsInFly.push(new OperatorInCode(operator, child.getText()));

    return ctx;
  }

  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    if (ctx.IDENTIFIER() != null) {
      operands.push(TerminalSymbolNode.identifier(ctx.IDENTIFIER()));
    } else {
      var childVariant = ctx.children.get(0);
      childVariant.accept(this);
    }

    var modifiers = ctx.modifier();
    for (var modifier : modifiers) {
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
    if (typeName == null) {
      // function style
      var typeNameArg = args.get(0);
      args = args.stream().skip(1).collect(Collectors.toList());
      callNode = ConstructorCallNode.createDynamic(makeSubexpression(typeNameArg.expression()));
    } else {
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
    var operation = BinaryOperationNode.create(
      BslOperator.DEREFERENCE,
      target,
      TerminalSymbolNode.identifier(ctx.IDENTIFIER()), "");
    operation.setRepresentingAst(ctx);
    operands.push(operation);
    return ctx;
  }

  @Override
  public ParseTree visitAccessIndex(BSLParser.AccessIndexContext ctx) {
    var target = operands.pop();

    var expressionArg = makeSubexpression(ctx.expression());

    var indexOperation = BinaryOperationNode.create(BslOperator.INDEX_ACCESS, target, expressionArg, "");
    indexOperation.setRepresentingAst(ctx);
    operands.push(indexOperation);
    return ctx;
  }

  @Override
  public ParseTree visitAccessCall(BSLParser.AccessCallContext ctx) {
    var target = operands.pop();
    var methodCall = ctx.methodCall();
    var callNode = MethodCallNode.create(methodCall.methodName().IDENTIFIER());
    addCallArguments(callNode, methodCall.doCall().callParamList().callParam());
    var operation = BinaryOperationNode.create(BslOperator.DEREFERENCE, target, callNode, "");
    operation.setRepresentingAst(ctx);
    operands.push(operation);
    return ctx;
  }

  @Override
  public ParseTree visitTernaryOperator(BSLParser.TernaryOperatorContext ctx) {

    var ternary = TernaryOperatorNode.create(
      makeSubexpression(ctx.expression(0)),
      makeSubexpression(ctx.expression(1)),
      makeSubexpression(ctx.expression(2))
    );

    ternary.setRepresentingAst(ctx);
    operands.push(ternary);

    return ctx;
  }

  private BslExpression makeSubexpression(BSLParser.ExpressionContext ctx) {
    ctx.accept(this);
    var operand = operands.pop();
    if (operand.getRepresentingAst() == null)
      operand.setRepresentingAst(ctx);

    return operand;
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
    if (operatorsInFly.isEmpty()) {
      return;
    }

    var operator = operatorsInFly.pop();
    switch (operator.getOperator()) {
      case UNARY_MINUS:
      case UNARY_PLUS:
      case NOT:
        var operand = operands.pop();
        var operation = UnaryOperationNode.create(operator.getOperator(), operand, operator.getActualSourceCode());
        operands.push(operation);
        break;
      default:
        var right = operands.pop();
        var left = operands.pop();
        var binaryOp = BinaryOperationNode.create(operator.getOperator(), left, right, operator.getActualSourceCode());
        operands.push(binaryOp);
    }
  }
}
