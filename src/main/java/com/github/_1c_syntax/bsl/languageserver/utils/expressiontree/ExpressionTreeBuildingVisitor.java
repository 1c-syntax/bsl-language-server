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
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;


/**
 * Построитель дерева выражений из AST.
 * <p>
 * Посетитель AST, который находит выражения BSL и преобразует их в Expression Tree
 * для упрощенного анализа структуры выражений в диагностиках.
 */
public final class ExpressionTreeBuildingVisitor extends BSLParserBaseVisitor<ParseTree> {

  /**
   * @param actualSourceCode ИЛИ vs OR в диагностических сообщениях, как написано в коде
   */
  private record OperatorInCode(BslOperator operator, ParseTree actualSourceCode) {
    public int getPriority() {
      return operator.getPriority();
    }
  }

  private final Deque<BslExpression> operands = new ArrayDeque<>();
  private final Deque<OperatorInCode> operatorsInFly = new ArrayDeque<>();

  private BslExpression resultExpression;
  private int recursionLevel = -1;

  /**
   * Хелпер построения дерева выражения на основе готового AST выражения
   *
   * @param ctx AST выражения
   * @return дерево вычисления выражения
   */
  public static BslExpression buildExpressionTree(BSLParser.ExpressionContext ctx) {
    var instance = new ExpressionTreeBuildingVisitor();
    instance.visitExpression(ctx);
    return instance.getExpressionTree();
  }

  /**
   * @return результирующее выражение в виде дерева вычисления операций
   */
  public BslExpression getExpressionTree() {
    return resultExpression;
  }

  /**
   * @param ctx выражение для создания дерева
   * @return ParseTree стандартного интерфейса antlr4
   */
  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {

    var nestingCount = operatorsInFly.size();
    recursionLevel++;

    if (Trees.nodeContainsErrors(ctx) || (ctx.getChildCount() == 0 || ctx.children.stream().anyMatch(ErrorNode.class::isInstance))) {
      var errorExpressionNode = new ErrorExpressionNode(ctx);
      if (recursionLevel > 0) {
        operands.push(errorExpressionNode);
      } else {
        resultExpression = errorExpressionNode;
      }
      return ctx;
    }

    visitMember(ctx.member(0));
    var count = ctx.getChildCount();

    if (count > 1) {
      for (var i = 1; i < count; ++i) {
        var child = ctx.getChild(i);
        if (child instanceof BSLParser.OperationContext operationContext) {
          visitOperation(operationContext);
        } else if (child instanceof BSLParser.MemberContext memberContext) {
          visitMember(memberContext);
        } else if (child instanceof BSLParser.PreprocessorContext) {
          // просто пропускаем
        } else {
          throw new IllegalStateException();
        }
      }
    }

    var addToOperands = recursionLevel > 0;

    while (nestingCount < operatorsInFly.size()) {
      buildOperation();
    }

    var operation = operands.peek();

    // В случае ошибок парсинга выражения, operation может быть null
    if (operation == null) {
      recursionLevel--;
      return ctx;
    }

    if (operation.getRepresentingAst() == null) {
      operation.setRepresentingAst(ctx);
    }

    if (!addToOperands) {
      resultExpression = operands.pop();
    }

    recursionLevel--;
    return ctx;
  }

  @Override
  public ParseTree visitMember(BSLParser.MemberContext ctx) {

    // В случае ошибки парсинга member может быть пустой.
    if (Trees.nodeContainsErrors(ctx) || ctx.getChildCount() == 0 || ctx.children.stream().anyMatch(ErrorNode.class::isInstance)) {
      operands.push(new ErrorExpressionNode(ctx));
      return ctx;
    }

    // нужен ручной dispatch на конкретного child,
    // т.к. нет отдельного правила для подвыражения в скобках
    //  constValue
      // | complexIdentifier
      // | (( LPAREN expression RPAREN ) modifier*) // нечего оверрайдить !
      // | (IDENTIFIER | globalMethodCall)          // нечего оверрайдить !
      // | waitExpression

    var unaryModifier = ctx.unaryModifier();
    var childIndex = 0;

    if (unaryModifier != null) {
      visitUnaryModifier(unaryModifier);
      childIndex = ctx.children.indexOf(unaryModifier) + 1;
    }

    if (ctx.waitExpression() != null) {
      return visitWaitExpression(ctx.waitExpression());
    }

    var dispatchChild = ctx.getChild(childIndex);
    if (dispatchChild instanceof ErrorNode) {
      operands.push(new ErrorExpressionNode(dispatchChild));
    } else if (dispatchChild instanceof TerminalNode terminalNode) {
      var token = terminalNode.getSymbol().getType();

      // ручная диспетчеризация
      if (token == BSLLexer.LPAREN) {
        visitParenthesis(ctx.expression(), ctx.modifier());
      } else {
        operands.push(new ErrorExpressionNode(dispatchChild));
      }
    } else {
      dispatchChild.accept(this);
    }

    return ctx;
  }

  private void visitParenthesis(BSLParser.ExpressionContext expression,
                                List<? extends BSLParser.ModifierContext> modifiers) {

    // Handle the case where expression is empty (empty parentheses)
    if (expression == null || expression.getTokens().isEmpty()) {
      operands.push(new ErrorExpressionNode(expression));
      return;
    }

    var subExpr = makeSubexpression(expression);

    if (subExpr == null) {
      subExpr = new ErrorExpressionNode(expression);
    }

    operands.push(subExpr);

    for (var modifier : modifiers) {
      modifier.accept(this);
    }
  }

  private void visitAwaitedMember(ParseTree child) {
    // TODO: придумать как представлять WAIT и стоит ли
    child.accept(this);
  }

  @Override
  public ParseTree visitOperation(BSLParser.OperationContext ctx) {
    var operator = getOperator(ctx);
    processOperation(new OperatorInCode(operator, ctx));
    return ctx;
  }

  private void processOperation(OperatorInCode operator) {
    if (operatorsInFly.isEmpty()) {
      operatorsInFly.push(operator);
      return;
    }

    while (hasHigherPriorityOperatorsInFly(operator)) {
      buildOperation();
    }

    operatorsInFly.push(operator);
  }

  private boolean hasHigherPriorityOperatorsInFly(OperatorInCode operator) {
    var lastSeenOperator = operatorsInFly.peek();
    if (lastSeenOperator == null) {
      return false;
    }

    return lastSeenOperator.getPriority() > operator.getPriority();
  }

  private static BslOperator getOperator(BSLParser.OperationContext ctx) {
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

    var operator = switch (token) {
      case BSLLexer.PLUS -> BslOperator.UNARY_PLUS;
      case BSLLexer.MINUS -> BslOperator.UNARY_MINUS;
      case BSLLexer.NOT_KEYWORD -> BslOperator.NOT;
      default -> throw new IllegalArgumentException();
    };

    operatorsInFly.push(new OperatorInCode(operator, child));

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
    if (Trees.nodeContainsErrors(ctx)) {
      operands.push(new ErrorExpressionNode(ctx));
      return ctx;
    }

    var typeName = ctx.typeName();

    List<? extends BSLParser.CallParamContext> args;
    var call = ctx.doCall();
    if (call == null) {
      args = Collections.emptyList();
    } else {
      args = ctx.doCall().callParamList().callParam();
    }
    ConstructorCallNode callNode;
    if (typeName == null) {
      // function style
      var typeNameArg = args.get(0);
      if (typeNameArg.expression() == null) {
        operands.push(new ErrorExpressionNode(ctx));
        return ctx;
      }
      args = args.stream().skip(1).toList();
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
      TerminalSymbolNode.identifier(ctx.IDENTIFIER()), ctx);

    operands.push(operation);
    return ctx;
  }

  @Override
  public ParseTree visitAccessIndex(BSLParser.AccessIndexContext ctx) {
    var target = operands.pop();
    var expressionArg = makeSubexpression(ctx.expression());
    var indexOperation = BinaryOperationNode.create(BslOperator.INDEX_ACCESS, target, expressionArg, ctx);
    operands.push(indexOperation);
    return ctx;
  }

  @Override
  public ParseTree visitAccessCall(BSLParser.AccessCallContext ctx) {
    var target = operands.pop();
    var methodCall = ctx.methodCall();
    var callNode = MethodCallNode.create(methodCall.methodName().IDENTIFIER());
    addCallArguments(callNode, methodCall.doCall().callParamList().callParam());
    var operation = BinaryOperationNode.create(BslOperator.DEREFERENCE, target, callNode, ctx);
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

  private static BslExpression makeSubexpression(BSLParser.ExpressionContext ctx) {
    return buildExpressionTree(ctx);
  }

  private static void addCallArguments(AbstractCallNode callNode, List<? extends BSLParser.CallParamContext> args) {
    for (var parameter : args) {
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
    if (Objects.requireNonNull(operator.operator()) == BslOperator.UNARY_MINUS
      || operator.operator() == BslOperator.NOT
      || operator.operator() == BslOperator.UNARY_PLUS) {

      var operand = operands.pop();
      var operation = UnaryOperationNode.create(operator.operator(), operand, operator.actualSourceCode());
      operand.setParent(operation);
      operands.push(operation);
    } else {
      var right = operands.pop();
      var left = operands.pop();
      var binaryOp = BinaryOperationNode.create(operator.operator(), left, right, operator.actualSourceCode());

      left.setParent(binaryOp);
      right.setParent(binaryOp);

      operands.push(binaryOp);
    }
  }
}
