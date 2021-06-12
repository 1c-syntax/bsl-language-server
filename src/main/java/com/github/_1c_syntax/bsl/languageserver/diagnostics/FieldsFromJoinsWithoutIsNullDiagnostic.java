/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
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
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
public class FieldsFromJoinsWithoutIsNullDiagnostic extends AbstractSDBLVisitorDiagnostic {

  private static final Set<Integer> RULE_QUERIES = Set.of(SDBLParser.RULE_query,
    SDBLParser.RULE_temporaryTableMainQuery, SDBLParser.RULE_temporaryTableQuery);

  private static final Set<Integer> SELECT_STATEMENTS_ROOT = Set.of(SDBLParser.RULE_selectedField, SDBLParser.RULE_temporaryTableSelectedField);
  private static final Set<Integer> SELECT_STATEMENTS = Set.of(SDBLParser.RULE_selectedField,
    SDBLParser.RULE_temporaryTableSelectedField, SDBLParser.RULE_selectStatement);

  private static final Set<Integer> WHERE_STATEMENTS_ROOT = Set.of(SDBLParser.RULE_where);
  private static final Set<Integer> WHERE_STATEMENTS = Set.of(SDBLParser.RULE_where, SDBLParser.RULE_whereStatement);
  private static final Set<Integer> JOIN_STATEMENTS_ROOT = Set.of(SDBLParser.RULE_joinPart);
  private static final Set<Integer> JOIN_STATEMENTS = Set.of(SDBLParser.RULE_joinPart, SDBLParser.RULE_joinStatement);
  public static final int IS_NOT_NULL_EXPR_MEMBERS_COUNT = 4;
  public static final int IS_NULL_EXPR_MEMBERS_COUNT = 3;

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
      .filter(Objects::nonNull)
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
  }

  private boolean haveNotIsNullInsideWhere(BSLParserRuleContext queryCtx, String joinedTableName) {
    return Trees.getFirstChild(queryCtx, SDBLParser.RULE_where)
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_whereMember).stream())
      .anyMatch(whereMember -> haveFirstIsThenNotThenNullInsideWhereMember((BSLParserRuleContext) whereMember, joinedTableName)
        || haveNotIsNullInsideWhereMember((BSLParserRuleContext) whereMember, joinedTableName)
      );
  }

  private boolean haveFirstIsThenNotThenNullInsideWhereMember(BSLParserRuleContext whereMember, String joinedTableName) {
    if (whereMember.getChildCount() != IS_NOT_NULL_EXPR_MEMBERS_COUNT) {
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

  private Stream<SDBLParser.ColumnContext> haveMatchedExpressionForTable(
    String tableName, BSLParserRuleContext expression, Integer parentStatementIndex,
    Set<Integer> statements, Set<Integer> statementsRoot) {

    return Optional.of(expression)
      .filter(expr -> !haveFirstIsThenNullInsideWhereExpression(expr, tableName))
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, parentStatementIndex).stream())
      .flatMap(parseTree -> Trees.getFirstChild(parseTree, SDBLParser.RULE_statement).stream())
      .filter(statementContext -> statementContext.getRuleIndex() != SDBLParser.ISNULL)
      .flatMap(column -> Trees.getFirstChild(column, SDBLParser.RULE_column).stream())
      .filter(Objects::nonNull)
      .filter(ctx -> ctx instanceof SDBLParser.ColumnContext)
      .map(SDBLParser.ColumnContext.class::cast)
      .filter(columnContext -> checkColumn(tableName, columnContext, statements, statementsRoot));
  }

  private boolean haveFirstIsThenNullInsideWhereExpression(BSLParserRuleContext expression, String tableName) {
    return Trees.findAllRuleNodes(expression, SDBLParser.RULE_whereMember).stream()
      .anyMatch(ctx -> haveFirstIsThenNullInsideWhereMember((BSLParserRuleContext) ctx, tableName));
  }

  private boolean haveFirstIsThenNullInsideWhereMember(BSLParserRuleContext whereMember, String joinedTableName) {
    if (whereMember.getChildCount() != IS_NULL_EXPR_MEMBERS_COUNT) {
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

  private boolean isTerminalNode(ParseTree node, int nodeType) {
    return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == nodeType;
  }

  private boolean checkColumn(String tableName, SDBLParser.ColumnContext columnCtx,
                              Set<Integer> statements, Set<Integer> statementsRoot) {
    return Optional.of(columnCtx)
      .filter(columnContext -> columnContext.tableName.getText().equalsIgnoreCase(tableName))
      .filter(columnContext -> !haveIsNullInside(columnContext, statements, statementsRoot))
      .isPresent();
  }

  private boolean haveIsNullInside(BSLParserRuleContext ctx, Set<Integer> statements, Set<Integer> rootParentIndex) {
    var selectStatement = Trees.getRootParent(ctx, statements);
    if (selectStatement == null || rootParentIndex.contains(selectStatement.getRuleIndex()) || selectStatement.getChildCount() == 0) {
      return false;
    }
    final var child = selectStatement.getChild(0);
    if (isTerminalNode(child, SDBLParser.ISNULL)) {
      return true;
    }
    return haveIsNullInside((BSLParserRuleContext) selectStatement.getParent(), statements, rootParentIndex);
  }

  private boolean haveNotIsNullInsideWhereMember(BSLParserRuleContext whereMember, String joinedTableName) {
    if (Trees.getFirstChild(whereMember, SDBLParser.RULE_whereStatement)
      .filter(ctx -> ctx.getChildCount() == 0 || !isTerminalNode(ctx.getChild(0), SDBLParser.NOT))
      .isPresent()) {
      return false;
    }
    return haveFirstIsThenNullInsideWhereExpression(whereMember, joinedTableName);
  }

  private void checkSelect(String tableName, BSLParserRuleContext query) {
    Trees.getFirstChild(query, SDBLParser.RULE_selectedFields, SDBLParser.RULE_temporaryTableSelectedFields)
      .ifPresent(ctx -> checkStatements(tableName, ctx, SDBLParser.RULE_selectStatement,
        SELECT_STATEMENTS, SELECT_STATEMENTS_ROOT));
  }

  private void checkStatements(
    String tableName, BSLParserRuleContext expression, Integer parentStatementIndex,
    Set<Integer> statements, Set<Integer> statementsRoot) {

    haveMatchedExpressionForTable(tableName, expression, parentStatementIndex, statements, statementsRoot)
      .forEach(nodesForIssues::add);
  }

  private void checkWhere(String tableName, BSLParserRuleContext query) {
    Trees.getFirstChild(query, SDBLParser.RULE_where)
      .filter(bslParserRuleContext -> bslParserRuleContext.getChildCount() > 0)
      .map(SDBLParser.WhereContext.class::cast)
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
