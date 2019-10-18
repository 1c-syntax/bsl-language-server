package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@DiagnosticMetadata(
    type = DiagnosticType.ERROR,
    severity = DiagnosticSeverity.BLOCKER,
    minutesToFix = 1
)
public class UnaryPlusInConcatenationDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {
    boolean isError = ctx.children.size() == 3
      && ctx.children.get(1).getChildCount() == 1
      && ctx.children.get(1).getChild(0).toString().equals("+")
      && ctx.children.get(2).getChildCount() == 2
      && ctx.children.get(2).getChild(0).getChild(0).toString().equals("+");
    if (isError) {
      diagnosticStorage.addDiagnostic((TerminalNode) ctx.children.get(1).getChild(0));
    }
    return super.visitExpression(ctx);
  }

}
