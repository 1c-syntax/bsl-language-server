package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  modules = {
    ModuleType.FormModule,
    ModuleType.ObjectModule,
    ModuleType.RecordSetModule,
    ModuleType.CommonModule
  },
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.DEPRECATED
  }
)
public class ExcessiveAutoTestCheckDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern ERROR_EXPRESSION = Pattern.compile(
    "(\\.Свойство\\(\"АвтоТест\"\\)|=\"АвтоТест\"|\\.Property\\(\"AutoTest\"\\)|=\"AutoTest\")$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public ExcessiveAutoTestCheckDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitIfBranch(BSLParser.IfBranchContext ctx) {

    if (expressionMatchesPattern(ctx.expression()) && codeBlockWithOnlyReturn(ctx.codeBlock())) {
      diagnosticStorage.addDiagnostic((BSLParser.IfStatementContext) ctx.getParent());
      return ctx;
    }

    return super.visitIfBranch(ctx);
  }

  private boolean expressionMatchesPattern(BSLParser.ExpressionContext expression) {
    return ERROR_EXPRESSION.matcher(expression.getText()).find();
  }

  private boolean codeBlockWithOnlyReturn(BSLParser.CodeBlockContext codeBlock) {
    return codeBlock
      .getTokens()
      .stream()
      .map(t -> t.getType())
      .filter(t -> t != BSLParser.WHITE_SPACE)
      .filter(t -> t != BSLParser.LINE_COMMENT)
      .filter(t -> t != BSLParser.SEMICOLON)
      .noneMatch(t -> t != BSLParser.RETURN_KEYWORD);
  }
}
