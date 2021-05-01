package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class FieldsFromConnectionsWithoutIsNullDiagnostic extends AbstractSDBLVisitorDiagnostic {

  private static final List<Integer> RULE_QUERIES = Arrays.asList(SDBLParser.RULE_query, SDBLParser.RULE_temporaryTableMainQuery);

  private static final Integer SELECTED_FIELD_INDEX = SDBLParser.RULE_selectedField;
  private static final List<Integer> SELECT_STATEMENTS = Arrays.asList(SDBLParser.RULE_selectStatement, SELECTED_FIELD_INDEX);

  @Override
  public ParseTree visitJoinPart(SDBLParser.JoinPartContext ctx) {
    final var tableName = Optional.of(ctx)
      .map(SDBLParser.JoinPartContext::dataSource)
      // TODO алиас не всегда задан, проверить без него
      .map(SDBLParser.DataSourceContext::alias)
      .map(SDBLParser.AliasContext::identifier)
      .map(BSLParserRuleContext::getText)
      .orElse("");

    BSLParserRuleContext query = Trees.getRootParent(ctx, RULE_QUERIES);
//    TODO проверить и RULE_query и RULE_temporaryTableMainQuery

    // TODO нужно проверять любые выражения, а не только из ВЫБРАТЬ
    final var allSelectStatement = Trees.findAllRuleNodes(query, SDBLParser.RULE_selectStatement);
    allSelectStatement.stream().
      map(parseTree -> (SDBLParser.SelectStatementContext) parseTree)
      .map(SDBLParser.SelectStatementContext::statement)
      .filter(Objects::nonNull)
      .filter(statementContext -> statementContext.getRuleIndex() != SDBLParser.ISNULL)
      .map(SDBLParser.StatementContext::column)
      .filter(Objects::nonNull)
      .filter(columnContext -> columnContext.tableName.getText().equalsIgnoreCase(tableName))
      .filter(this::dontInnerIsNull)
      .forEach(diagnosticStorage::addDiagnostic);
    return super.visitJoinPart(ctx);
  }

  private boolean dontInnerIsNull(BSLParserRuleContext ctx) {
    //TODO может быть не только RULE_statement
    BSLParserRuleContext selectStatement = Trees.getRootParent(ctx, SELECT_STATEMENTS);
    if (selectStatement == null || selectStatement.getRuleIndex() == SELECTED_FIELD_INDEX || selectStatement.getChildCount() == 0){
      return true;
    }
    final var child = selectStatement.getChild(0);
    if (child instanceof TerminalNode && ((TerminalNode)child).getSymbol().getType() == SDBLParser.ISNULL){
      return false;
    }
    return dontInnerIsNull((BSLParserRuleContext)selectStatement.getParent());
  }
}
