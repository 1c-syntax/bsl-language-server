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
  private static final List<Integer> SELECT_STATEMENTS = Arrays.asList(SELECTED_FIELD_INDEX, SDBLParser.RULE_selectStatement);
  private static final Integer WHERE_INDEX = SDBLParser.RULE_where;
  private static final List<Integer> WHERE_STATEMENTS = Arrays.asList(WHERE_INDEX, SDBLParser.RULE_whereStatement);

  @Override
  public ParseTree visitJoinPart(SDBLParser.JoinPartContext ctx) {
    final var tableName = Optional.of(ctx)
      .map(SDBLParser.JoinPartContext::dataSource)
      // TODO алиас не всегда задан, проверить без него
      .map(SDBLParser.DataSourceContext::alias)
      .map(SDBLParser.AliasContext::identifier)
      .map(BSLParserRuleContext::getText)
      .orElse("");

    Optional.ofNullable(Trees.getRootParent(ctx, RULE_QUERIES))
      .ifPresent(queryCtx -> {
        checkSelect(tableName, queryCtx);
        checkWhere(tableName, queryCtx);
      });
//    TODO проверить и RULE_query и RULE_temporaryTableMainQuery

    // TODO нужно проверять любые выражения, а не только из ВЫБРАТЬ

    return super.visitJoinPart(ctx);
  }

  private void checkSelect(String tableName, BSLParserRuleContext query) {
    final var selectedFieldsCtx = Trees.getFirstChild(query, SDBLParser.RULE_selectedFields);
    selectedFieldsCtx
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_selectStatement).stream())
      .map(parseTree -> (SDBLParser.SelectStatementContext) parseTree)
      .map(SDBLParser.SelectStatementContext::statement)
      .filter(Objects::nonNull)
      .filter(statementContext -> statementContext.getRuleIndex() != SDBLParser.ISNULL)
      .map(SDBLParser.StatementContext::column)
      .filter(Objects::nonNull)
      .filter(columnContext -> columnContext.tableName.getText().equalsIgnoreCase(tableName))
      .filter(columnContext -> dontInnerIsNull(columnContext, SELECT_STATEMENTS, SELECTED_FIELD_INDEX))
      .forEach(diagnosticStorage::addDiagnostic);
  }

  private void checkWhere(String tableName, BSLParserRuleContext query) {
    final var whereCtx = Trees.getFirstChild(query, SDBLParser.RULE_where);
    whereCtx
      .filter(bslParserRuleContext -> bslParserRuleContext.getChildCount() > 0)
      .map(ctx -> (SDBLParser.WhereContext)ctx)
      .map(SDBLParser.WhereContext::whereExpression)
//      .flatMap(ctx -> Trees.getFirstChild(query, SDBLParser.RULE_whereExpression))
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_whereStatement).stream())
      .map(parseTree -> (SDBLParser.WhereStatementContext) parseTree)
      .map(SDBLParser.WhereStatementContext::statement)
      .filter(Objects::nonNull)
      .filter(statementContext -> statementContext.getRuleIndex() != SDBLParser.ISNULL)
      .map(SDBLParser.StatementContext::column)
      .filter(Objects::nonNull)
      .filter(columnContext -> columnContext.tableName.getText().equalsIgnoreCase(tableName))
      .filter(columnContext -> dontInnerIsNull(columnContext, WHERE_STATEMENTS, WHERE_INDEX))
      .forEach(diagnosticStorage::addDiagnostic);
  }

  private boolean dontInnerIsNull(BSLParserRuleContext ctx, List<Integer> statements, Integer rootParentIndex) {
    BSLParserRuleContext selectStatement = Trees.getRootParent(ctx, statements);
    if (selectStatement == null || selectStatement.getRuleIndex() == rootParentIndex || selectStatement.getChildCount() == 0){
      return true;
    }
    final var child = selectStatement.getChild(0);
    if (child instanceof TerminalNode && ((TerminalNode)child).getSymbol().getType() == SDBLParser.ISNULL){
      return false;
    }
    return dontInnerIsNull((BSLParserRuleContext)selectStatement.getParent(), statements, rootParentIndex);
  }
}
