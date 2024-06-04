package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

/**
 * Обходчик дерева выражений
 */
public class ExpressionTreeVisitor {

  private void visit(BslExpression node) {
    switch (node.getNodeType()) {
      case CALL:
        visitAbstractCall((AbstractCallNode) node);
        break;
      case UNARY_OP:
        visitUnaryOperation((UnaryOperationNode) node);
        break;
      case TERNARY_OP:
        var ternary = (TernaryOperatorNode) node;
        visitTernaryOperator(ternary);
        break;
      case BINARY_OP:
        visitBinaryOperation((BinaryOperationNode)node);
        break;

      default:
        break; // для спокойствия сонара
    }
  }

  protected void visitTopLevelExpression(BslExpression node) {
    visit(node);
  }

  protected void visitAbstractCall(AbstractCallNode node) {
    for (var expr : node.arguments()) {
      visit(expr);
    }
  }

  protected void visitUnaryOperation(UnaryOperationNode node) {
    visit(node.getOperand());
  }

  protected void visitBinaryOperation(BinaryOperationNode node) {
    visit(node.getLeft());
    visit(node.getRight());
  }

  protected void visitTernaryOperator(TernaryOperatorNode node) {
    visit(node.getCondition());
    visit(node.getTruePart());
    visit(node.getFalsePart());
  }
}
