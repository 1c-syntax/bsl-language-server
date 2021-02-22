package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.UNPREDICTABLE
  },
  scope = DiagnosticScope.BSL

)
public class LogicalOrInTheWhereSectionOfQueryDiagnostic extends AbstractSDBLVisitorDiagnostic {

  @Override
  public ParseTree visitBoolOperation(SDBLParser.BoolOperationContext ctx) {

    TerminalNode orNode = ctx.OR();
    if (orNode != null) {
      BSLParserRuleContext whereCtx = Trees.getRootParent(ctx, SDBLParser.RULE_whereExpression);
      if (whereCtx != null){
        diagnosticStorage.addDiagnostic(orNode);
      }
    }
    return super.visitBoolOperation(ctx);
  }
}
