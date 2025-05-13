package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class LogicalOrInJoinQuerySectionDiagnostic extends AbstractSDBLVisitorDiagnostic {

  @Override
  public ParseTree visitQuery(SDBLParser.QueryContext ctx) {
    Trees.findAllRuleNodes(ctx, SDBLParser.RULE_joinPart).
      forEach(jpt -> processJoinPart((SDBLParser.JoinPartContext) jpt));
    return ctx;
  }

  private void processJoinPart(SDBLParser.JoinPartContext ctx) {

      ctx.condition.condidions.stream().map(SDBLParser.PredicateContext::logicalExpression)
        .filter(Objects::nonNull)
        .filter(this::isMultipleFieldsExpression)
        .forEach(
          exp -> Trees.findAllTokenNodes(exp, SDBLParser.OR)
            .forEach(diagnosticStorage::addDiagnostic));

  }

  private boolean isMultipleFieldsExpression(SDBLParser.LogicalExpressionContext exp){

    Set<String> expFields = Trees.findAllRuleNodes(exp, SDBLParser.RULE_column).stream()
      .map(ParseTree::getText)
      .collect(Collectors.toSet());
    return expFields.size() > 1;
  }

}
