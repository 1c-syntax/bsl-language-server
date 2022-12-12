package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.Deque;

class PreprocessorExpressionTreeBuildingVisitor extends BSLParserBaseVisitor<ParseTree> {

  private BslExpression resultExpression;

  private final Deque<State> states = new ArrayDeque<>();

  BslExpression getExpressionTree() {
    return resultExpression;
  }

  @Override
  public ParseTree visitPreproc_expression(BSLParser.Preproc_expressionContext ctx) {

    boolean isRoot = states.isEmpty();
    var currentState = new State();
    states.push(currentState);

    super.visitPreproc_expression(ctx);

    if (ctx.PREPROC_NOT_KEYWORD() != null) {
      pushNot(ctx);
    }
    states.remove();

    if (isRoot) {
      resultExpression = currentState.operands.pop();
    } else {
      getOperands().push(currentState.operands.pop());
    }

    return ctx;
  }

  @Override
  public ParseTree visitPreproc_logicalExpression(BSLParser.Preproc_logicalExpressionContext ctx) {

    var currentState = new State();
    states.push(currentState);

    super.visitPreproc_logicalExpression(ctx);

    while (!currentState.operators.isEmpty()) {
      buildOperation(ctx);
    }

    states.remove();
    getOperands().push(currentState.operands.pop());

    return ctx;
  }

  @Override
  public ParseTree visitPreproc_logicalOperand(BSLParser.Preproc_logicalOperandContext ctx) {
    var operands = getOperands();

    if (ctx.preproc_symbol() != null) {
      operands.push(new PreprocessorSymbolNode(ctx.preproc_symbol()));
    } else {
      super.visitPreproc_logicalOperand(ctx);
    }

    if (ctx.PREPROC_NOT_KEYWORD() != null) {
      pushNot(ctx);
    }

    return ctx;
  }

  @Override
  public ParseTree visitPreproc_boolOperation(BSLParser.Preproc_boolOperationContext ctx) {
    if (ctx.PREPROC_AND_KEYWORD() != null) {
      processOperation(BslOperator.AND, ctx);
    } else if (ctx.PREPROC_OR_KEYWORD() != null) {
      processOperation(BslOperator.OR, ctx);
    }
    return ctx;
  }

  private void processOperation(BslOperator operator, ParseTree ctx) {
    var operators = getOperators();
    if (operators.isEmpty()) {
      operators.push(operator);
      return;
    }

    var lastSeenOperator = operators.peek();
    if (lastSeenOperator.getPriority() > operator.getPriority()) {
      buildOperation(ctx);
    }

    operators.push(operator);
  }

  private void buildOperation(ParseTree ctx) {
    var operators = getOperators();
    if (operators.isEmpty()) {
      return;
    }

    var operands = getOperands();
    var operator = operators.pop();
    if (operator == BslOperator.NOT) {
      var operand = operands.pop();
      var operation = UnaryOperationNode.create(operator, operand, ctx);
      operands.push(operation);
    } else {
      var right = operands.pop();
      var left = operands.pop();
      var binaryOp = BinaryOperationNode.create(operator, left, right, ctx);
      operands.push(binaryOp);
    }
  }

  private Deque<BslExpression> getOperands() {
    if (states.isEmpty()) {
      throw new IllegalStateException();
    }
    return states.peek().operands;
  }

  private Deque<BslOperator> getOperators() {
    if (states.isEmpty()) {
      throw new IllegalStateException();
    }
    return states.peek().operators;
  }

  private void pushNot(ParseTree ctx) {
    var operators = getOperators();
    operators.push(BslOperator.NOT);
    buildOperation(ctx);
  }

  private static class State {
    private final Deque<BslExpression> operands = new ArrayDeque<>();
    private final Deque<BslOperator> operators = new ArrayDeque<>();

  }
}
