package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import lombok.val;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 2,
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

  private final List<BSLParserRuleContext> nodesForIssues = new ArrayList<>();

  @Override
  public ParseTree visitJoinPart(SDBLParser.JoinPartContext joinPartCtx) {

    try {
      joinedTables(joinPartCtx)
        .forEach(tableName -> checkQuery(tableName, joinPartCtx));

      if (!nodesForIssues.isEmpty()) {
        diagnosticStorage.addDiagnostic(joinPartCtx, getRelatedInformation(joinPartCtx));
      }

    } catch (Exception e) {
      nodesForIssues.clear();
      throw e;
    }
    nodesForIssues.clear();

    return super.visitJoinPart(joinPartCtx);
  }

  private Stream<String> joinedTables(SDBLParser.JoinPartContext joinPartCtx) {
    return Optional.of(joinPartCtx)
      .stream().flatMap(joinPartContext -> joinedDataSourceContext(joinPartContext).stream())
      .filter(Objects::nonNull)
      .map(SDBLParser.DataSourceContext::alias)
      .map(SDBLParser.AliasContext::identifier)
      .map(BSLParserRuleContext::getText);
  }

  private List<SDBLParser.DataSourceContext> joinedDataSourceContext(SDBLParser.JoinPartContext joinPartContext) {
    if (joinPartContext.LEFT() != null) {
      return Collections.singletonList(joinPartContext.dataSource());
    } else if (joinPartContext.RIGHT() != null) {
      return Collections.singletonList(((SDBLParser.DataSourceContext) joinPartContext.getParent()));
    } else if (joinPartContext.FULL() != null) {
      return Arrays.asList(((SDBLParser.DataSourceContext) joinPartContext.getParent()),
        joinPartContext.dataSource());
    }
    return Collections.emptyList();
  }

  private void checkQuery(String joinedTableName, SDBLParser.JoinPartContext joinPartCtx) {
    Optional.ofNullable(Trees.getRootParent(joinPartCtx, RULE_QUERIES))
      .filter(ctx -> !haveNotIsNullInsideWhere(ctx, joinedTableName))
      .ifPresent(queryCtx -> {
        checkSelect(joinedTableName, queryCtx);
        checkWhere(joinedTableName, queryCtx);

        checkAllJoins(joinedTableName, joinPartCtx);
      });

//    TODO проверить и RULE_query и RULE_temporaryTableMainQuery

// TODO нужно проверять любые выражения - из СГРУППИРОВАТЬ, ИМЕЮЩИЕ и т.п.
  }

  private boolean haveNotIsNullInsideWhere(BSLParserRuleContext queryCtx, String joinedTableName) {
    return Trees.getFirstChild(queryCtx, SDBLParser.RULE_where)
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_whereMember).stream())
      .anyMatch(whereMember -> haveFirstIsThenNotThenNullInsideWhereMember((BSLParserRuleContext) whereMember, joinedTableName)
        || haveNotIsNullInsideWhereMember((BSLParserRuleContext) whereMember, joinedTableName)
      );
  }

  private boolean haveFirstIsThenNotThenNullInsideWhereMember(BSLParserRuleContext whereMember, String joinedTableName) {
    if (whereMember.getChildCount() != 4) {
      return false;
    }
    final var childIS = whereMember.getChild(1);
    final var childNOT = whereMember.getChild(2);
    final var childNULL = whereMember.getChild(3);

    final var matched = isTerminalNode(childIS, SDBLParser.IS) &&
      isTerminalNode(childNOT, SDBLParser.NOT) &&
      isTerminalNode(childNULL, SDBLParser.NULL);
    if (!matched) {
      return false;
    }

    return matchExpressionInsideWhereMember(whereMember, joinedTableName);
  }

  private boolean matchExpressionInsideWhereMember(BSLParserRuleContext whereMember, String joinedTableName) {
    return haveMatchedExpressionForTable(joinedTableName, (BSLParserRuleContext) whereMember.getChild(0),
      SDBLParser.RULE_whereStatement, WHERE_STATEMENTS, WHERE_STATEMENTS_ROOT)
      .findAny().isPresent();
  }

  private Stream<SDBLParser.ColumnContext> haveMatchedExpressionForTable(String tableName, BSLParserRuleContext expression, Integer parentStatementIndex, List<Integer> statements, Integer statementsRoot) {
    return Optional.of(expression)
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, parentStatementIndex).stream())
      .flatMap(parseTree -> Trees.getFirstChild(parseTree, SDBLParser.RULE_statement).stream())
      .filter(statementContext -> statementContext.getRuleIndex() != SDBLParser.ISNULL)
      .flatMap(column -> Trees.getFirstChild(column, SDBLParser.RULE_column).stream())
      .filter(ctx -> ctx instanceof SDBLParser.ColumnContext)
      .map(ctx -> (SDBLParser.ColumnContext) ctx)
      .filter(columnContext -> checkColumn(tableName, columnContext, statements, statementsRoot));
  }

  private boolean checkColumn(String tableName, SDBLParser.ColumnContext columnCtx,
                              List<Integer> statements, Integer statementsRoot) {
    return Optional.of(columnCtx)
      .filter(columnContext -> columnContext.tableName.getText().equalsIgnoreCase(tableName))
      .filter(columnContext -> !haveIsNullInside(columnContext, statements, statementsRoot))
      .isPresent();
  }

  private boolean haveIsNullInside(BSLParserRuleContext ctx, List<Integer> statements, Integer rootParentIndex) {
    var selectStatement = Trees.getRootParent(ctx, statements);
    if (selectStatement == null || selectStatement.getRuleIndex() == rootParentIndex || selectStatement.getChildCount() == 0) {
      return false;
    }
    final var child = selectStatement.getChild(0);
    if (isTerminalNode(child, SDBLParser.ISNULL)) {
      return true;
    }
    return haveIsNullInside((BSLParserRuleContext) selectStatement.getParent(), statements, rootParentIndex);
  }

  private boolean isTerminalNode(ParseTree node, int nodeType) {
    return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == nodeType;
  }

  private boolean haveNotIsNullInsideWhereMember(BSLParserRuleContext whereMember, String joinedTableName) {
    if (Trees.getFirstChild(whereMember, SDBLParser.RULE_whereStatement)
      .filter(ctx -> ctx.getChildCount() == 0 || !isTerminalNode(ctx.getChild(0), SDBLParser.NOT))
      .isPresent()) {
      return false;
    }
    return Trees.findAllRuleNodes(whereMember, SDBLParser.RULE_whereMember).stream()
      .anyMatch(ctx -> haveFirstIsThenNullInsideWhereMember((BSLParserRuleContext) ctx, joinedTableName));
  }

  private boolean haveFirstIsThenNullInsideWhereMember(BSLParserRuleContext whereMember, String joinedTableName) {
    if (whereMember.getChildCount() != 3) {
      return false;
    }
    final var childIS = whereMember.getChild(1);
    final var childNULL = whereMember.getChild(2);

    final var matched = isTerminalNode(childIS, SDBLParser.IS) &&
      isTerminalNode(childNULL, SDBLParser.NULL);
    if (!matched) {
      return false;
    }
    return matchExpressionInsideWhereMember(whereMember, joinedTableName);
  }

  private void checkSelect(String tableName, BSLParserRuleContext query) {
    Trees.getFirstChild(query, SDBLParser.RULE_selectedFields)
      .ifPresent(ctx -> checkStatements(tableName, ctx, SDBLParser.RULE_selectStatement,
        SELECT_STATEMENTS, SELECT_STATEMENTS_ROOT));
  }

  private void checkStatements(
    String tableName, BSLParserRuleContext expression, Integer parentStatementIndex,
    List<Integer> statements, Integer statementsRoot) {

    haveMatchedExpressionForTable(tableName, expression, parentStatementIndex, statements, statementsRoot)
      .forEach(nodesForIssues::add);
  }

  private void checkWhere(String tableName, BSLParserRuleContext query) {
    Trees.getFirstChild(query, SDBLParser.RULE_where)
      .filter(bslParserRuleContext -> bslParserRuleContext.getChildCount() > 0)
      .map(ctx -> (SDBLParser.WhereContext) ctx)
      .map(SDBLParser.WhereContext::whereExpression)
      .ifPresent(exprCtx -> checkStatements(tableName, exprCtx, SDBLParser.RULE_whereStatement,
        WHERE_STATEMENTS, WHERE_STATEMENTS_ROOT));
  }

  private void checkAllJoins(String tableName, SDBLParser.JoinPartContext currentJoinPart) {
    Optional.ofNullable(Trees.getRootParent(currentJoinPart, SDBLParser.RULE_dataSource))
      .filter(ctx -> ctx instanceof SDBLParser.DataSourceContext)
      .stream().flatMap(ctx -> ((SDBLParser.DataSourceContext) ctx).joinPart().stream())
      .filter(joinPartContext -> joinPartContext != currentJoinPart)
      .map(SDBLParser.JoinPartContext::joinExpression)
      .forEach(joinExpressionContext -> checkStatements(tableName, joinExpressionContext, SDBLParser.RULE_joinStatement,
        JOIN_STATEMENTS, JOIN_STATEMENTS_ROOT));
  }

  private List<DiagnosticRelatedInformation> getRelatedInformation(SDBLParser.JoinPartContext self) {
    return nodesForIssues.stream()
      .filter(ctx -> !ctx.equals(self))
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(context),
        "+1"
      )).collect(Collectors.toList());
  }
}
