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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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

  private static final Integer SELECT_STATEMENTS_ROOT = SDBLParser.RULE_selectedField;
  private static final List<Integer> SELECT_STATEMENTS = Arrays.asList(SELECT_STATEMENTS_ROOT, SDBLParser.RULE_selectStatement);
  private static final Integer WHERE_STATEMENTS_ROOT = SDBLParser.RULE_where;
  private static final List<Integer> WHERE_STATEMENTS = Arrays.asList(WHERE_STATEMENTS_ROOT, SDBLParser.RULE_whereStatement);
  private static final Integer JOIN_STATEMENTS_ROOT = SDBLParser.RULE_joinPart;
  private static final List<Integer> JOIN_STATEMENTS = Arrays.asList(JOIN_STATEMENTS_ROOT, SDBLParser.RULE_joinStatement);

  @Override
  public ParseTree visitJoinPart(SDBLParser.JoinPartContext ctx) {
    final var tableName = Optional.of(ctx)
      .map(this::getDataSourceContext)
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

//    checkJoin(tableName, ctx.joinExpression());
    // TODO нужно проверять любые выражения, а не только из ВЫБРАТЬ

    return super.visitJoinPart(ctx);
  }

  @Nullable
  private SDBLParser.DataSourceContext getDataSourceContext(SDBLParser.JoinPartContext joinPartContext) {
    if (joinPartContext.LEFT() != null){
      return joinPartContext.dataSource();
    }
    else if(joinPartContext.RIGHT() != null){
      return ((SDBLParser.DataSourceContext) joinPartContext.getParent());
    }
    // TODO проверить ПОЛНОЕ ВНЕШНЕЕ СОЕДИНЕНИЕ - обе таблицы нужно проверять
//        else if(joinPartContext.FULL() != null){
//          return ((SDBLParser.DataSourceContext)joinPartContext.getParent());
//        }
    return null;
  }

  private void checkSelect(String tableName, BSLParserRuleContext query) {
    final var selectedFieldsCtx = Trees.getFirstChild(query, SDBLParser.RULE_selectedFields);
    final var columnContextStream = selectedFieldsCtx
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_selectStatement).stream())
      .filter(parseTree -> parseTree instanceof SDBLParser.SelectStatementContext)
      .map(parseTree -> ((SDBLParser.SelectStatementContext) parseTree).statement())
      .filter(Objects::nonNull)
      .filter(statementContext -> statementContext.getRuleIndex() != SDBLParser.ISNULL)
      .map(SDBLParser.StatementContext::column);

    checkColumn(tableName, columnContextStream, SELECT_STATEMENTS, SELECT_STATEMENTS_ROOT);
  }

  private void checkColumn(String tableName, Stream<SDBLParser.ColumnContext> columnContextStream, List<Integer> statements, Integer statementsRoot) {
    columnContextStream.filter(Objects::nonNull)
      .filter(columnContext -> columnContext.tableName.getText().equalsIgnoreCase(tableName))
      .filter(columnContext -> dontInnerIsNull(columnContext, statements, statementsRoot))
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

  private void checkWhere(String tableName, BSLParserRuleContext query) {
    final var whereCtx = Trees.getFirstChild(query, SDBLParser.RULE_where);
    final var columnContextStream = whereCtx
      .filter(bslParserRuleContext -> bslParserRuleContext.getChildCount() > 0)
      .map(ctx -> (SDBLParser.WhereContext) ctx)
      .map(SDBLParser.WhereContext::whereExpression)
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_whereStatement).stream())
      .filter(parseTree -> parseTree instanceof SDBLParser.WhereStatementContext)
      .map(parseTree -> ((SDBLParser.WhereStatementContext) parseTree).statement())
      .filter(Objects::nonNull)
      .filter(statementContext -> statementContext.getRuleIndex() != SDBLParser.ISNULL)
      .map(SDBLParser.StatementContext::column);

    checkColumn(tableName, columnContextStream, WHERE_STATEMENTS, WHERE_STATEMENTS_ROOT);
  }

  private void checkJoin(String tableName, SDBLParser.JoinExpressionContext joinExpression) {
    final var columnContextStream = Optional.of(joinExpression)
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_joinStatement).stream())
      .filter(parseTree -> parseTree instanceof SDBLParser.JoinStatementContext)
      .map(parseTree -> ((SDBLParser.JoinStatementContext) parseTree).statement())
      .filter(Objects::nonNull)
      .filter(statementContext -> statementContext.getRuleIndex() != SDBLParser.ISNULL)
      .map(SDBLParser.StatementContext::column);

    checkColumn(tableName, columnContextStream, JOIN_STATEMENTS, JOIN_STATEMENTS_ROOT);
  }
}
