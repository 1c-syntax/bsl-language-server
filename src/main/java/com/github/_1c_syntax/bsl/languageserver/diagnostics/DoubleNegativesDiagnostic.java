package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionParseTreeRewriter;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 3,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.BADPRACTICE
  }
)
public class DoubleNegativesDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MIN_EXPRESSION_SIZE = 3;

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {

    if (sufficientSize(ctx))
      return ctx;

    var tree = ExpressionParseTreeRewriter.buildExpressionTree(ctx);

    return ctx;
  }

  private static boolean sufficientSize(BSLParser.ExpressionContext ctx) {
    return ctx.children.size() < MIN_EXPRESSION_SIZE;
  }
}
