package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
    type = DiagnosticType.ERROR,
    severity = DiagnosticSeverity.BLOCKER,
    minutesToFix = 1
)
public class UnaryPlusInConcatenationDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx) {
    // ctx.children.stream().forEach()
    return super.visitStatement(ctx);
  }

  private static void addErrorsRecursively() {

  }
}
