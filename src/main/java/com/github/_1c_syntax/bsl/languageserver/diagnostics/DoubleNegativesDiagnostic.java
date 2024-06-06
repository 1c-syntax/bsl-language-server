package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.parser.BSLParser;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 3,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.BADPRACTICE
  }
)
public class DoubleNegativesDiagnostic extends AbstractExpressionTreeDiagnostic {

  @Override
  protected ExpressionVisitorDecision onExpressionEnter(BSLParser.ExpressionContext ctx) {
    return super.onExpressionEnter(ctx);
  }

  @Override
  protected void visitBinaryOperation(BinaryOperationNode node) {

    if (node.getOperator() != BslOperator.EQUAL && node.getOperator() != BslOperator.NOT_EQUAL) {
      super.visitBinaryOperation(node);
      return;
    }

    var parent = node.getParent();

    if (parent == null || !isNegationOperator(parent)) {
      super.visitBinaryOperation(node);
      return;
    }

    if (node.getOperator() == BslOperator.NOT_EQUAL) {
      addDiagnostic(node);
    } else if (isBooleanLiteral(node.getLeft()) || isBooleanLiteral(node.getRight())) {
      addDiagnostic(node);
    }

    super.visitBinaryOperation(node);
  }

  @Override
  protected void visitUnaryOperation(UnaryOperationNode node) {
    if (node.getOperator() == BslOperator.NOT &&
        node.getParent() != null &&
        node.getParent().getNodeType() == ExpressionNodeType.UNARY_OP) {

      var unaryParent = node.getParent().<UnaryOperationNode>cast();
      if (unaryParent.getOperator() == BslOperator.NOT) {
        addDiagnostic(node);
      }
    }

    super.visitUnaryOperation(node);
  }

  private boolean isBooleanLiteral(BslExpression node) {
    if (node.getNodeType() != ExpressionNodeType.LITERAL)
      return false;

    var constant = (BSLParser.ConstValueContext) node.getRepresentingAst();
    return constant.TRUE() != null || constant.FALSE() != null;
  }

  private static boolean isNegationOperator(BslExpression parent) {
    return parent.getNodeType() == ExpressionNodeType.UNARY_OP && parent.<UnaryOperationNode>cast().getOperator() == BslOperator.NOT;
  }

  private void addDiagnostic(BinaryOperationNode node) {
    var startToken = Trees.getTokens(node.getParent().getRepresentingAst()).stream().findFirst().orElseThrow();
    var endToken = Trees.getTokens(node.getRight().getRepresentingAst()).stream().reduce((one, two) -> two).orElseThrow();

    diagnosticStorage.addDiagnostic(startToken, endToken);
  }

  private void addDiagnostic(UnaryOperationNode node) {
    var startToken = Trees.getTokens(node.getParent().getRepresentingAst()).stream().findFirst().orElseThrow();
    var endToken = Trees.getTokens(node.getOperand().getRepresentingAst()).stream().reduce((one, two) -> two).orElseThrow();

    diagnosticStorage.addDiagnostic(startToken, endToken);
  }
}
