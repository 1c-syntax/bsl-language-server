/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
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
import com.github._1c_syntax.bsl.parser.SDBLParser;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
  activatedByDefault = false,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNPREDICTABLE
  }
)
public class FieldsFromJoinsWithoutIsNullDiagnostic extends AbstractSDBLVisitorDiagnostic {

  // схема расчета - находится поле из соединения,
  // далее идет поиск вверх по родительским узлам для проверки вхождения в разных вариациях ЕСТЬNULL или ЕСТЬ NULL
  // для оптимизации ищем вверх не до начального узла всего дерева, а до узла, в котором искать уже нет смысла

  private static final Integer EXCLUDED_TOP_RULE_FOR_SELECT = SDBLParser.RULE_selectedField;
  private static final Collection<Integer> SELECT_STATEMENTS = Set.of(EXCLUDED_TOP_RULE_FOR_SELECT,
    SDBLParser.RULE_builtInFunctions, SDBLParser.RULE_isNullPredicate);

  private static final Integer EXCLUDED_TOP_RULE_FOR_WHERE = SDBLParser.RULE_query;
  private static final Collection<Integer> WHERE_STATEMENTS = Set.of(EXCLUDED_TOP_RULE_FOR_WHERE,
    SDBLParser.RULE_builtInFunctions, SDBLParser.RULE_isNullPredicate);

  private static final Integer EXCLUDED_TOP_RULE_FOR_JOIN = SDBLParser.RULE_joinPart;
  private static final Collection<Integer> JOIN_STATEMENTS = Set.of(EXCLUDED_TOP_RULE_FOR_JOIN,
    SDBLParser.RULE_builtInFunctions);

  public static final Collection<Integer> RULES_OF_PARENT_FOR_SEARCH_CONDITION = Set.of(SDBLParser.RULE_predicate,
    SDBLParser.RULE_query);

  public static final int NOT_WITH_PARENS_EXPR_MEMBERS_COUNT = 4;
  public static final int NOT_IS_NULL_EXPR_MEMBER_COUNT = 2;

  private final List<ParserRuleContext> nodesForIssues = new ArrayList<>();

  @Override
  public ParseTree visitJoinPart(SDBLParser.JoinPartContext joinPartCtx) {

    joinedTables(joinPartCtx)
      .forEach(tableName -> checkQuery(tableName, joinPartCtx));

    if (!nodesForIssues.isEmpty()) {
      diagnosticStorage.addDiagnostic(joinPartCtx, getRelatedInformation(joinPartCtx));
      nodesForIssues.clear();
    }

    return super.visitJoinPart(joinPartCtx);
  }

  private static Stream<String> joinedTables(SDBLParser.JoinPartContext joinPartCtx) {
    return Optional.of(joinPartCtx)
      .stream().flatMap(joinPartContext -> joinedDataSourceContext(joinPartContext).stream())
      .filter(Objects::nonNull)
      .map(SDBLParser.DataSourceContext::alias)
      .filter(Objects::nonNull)
      .map(SDBLParser.AliasContext::identifier)
      .map(ParserRuleContext::getText);
  }

  private static List<SDBLParser.DataSourceContext> joinedDataSourceContext(SDBLParser.JoinPartContext joinPartCtx) {
    final List<SDBLParser.DataSourceContext> result;
    if (joinPartCtx.LEFT() != null) {
      result = Collections.singletonList(joinPartCtx.dataSource());
    } else if (joinPartCtx.RIGHT() != null) {
      result = Collections.singletonList(((SDBLParser.DataSourceContext) joinPartCtx.getParent()));
    } else if (joinPartCtx.FULL() != null) {
      result = Arrays.asList(((SDBLParser.DataSourceContext) joinPartCtx.getParent()),
        joinPartCtx.dataSource());
    } else {
      result = Collections.emptyList();
    }
    return result;
  }

  private void checkQuery(String joinedTableName, SDBLParser.JoinPartContext joinPartCtx) {
    Optional.ofNullable(Trees.getRootParent(joinPartCtx, SDBLParser.RULE_query))
      .map(SDBLParser.QueryContext.class::cast)
      .filter(ctx -> !haveExprNotIsNullInsideWhere(ctx.where))
      .ifPresent((SDBLParser.QueryContext queryCtx) -> {
        checkSelect(joinedTableName, queryCtx.columns);
        checkWhere(joinedTableName, queryCtx.where);

        checkAllJoins(joinedTableName, joinPartCtx);
      });
  }

  private static boolean haveExprNotIsNullInsideWhere(@Nullable SDBLParser.LogicalExpressionContext whereCtx) {
    return Optional.ofNullable(whereCtx)
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_isNullPredicate).stream())
      .map(SDBLParser.IsNullPredicateContext.class::cast)
      .anyMatch(isNullPredicateCtx ->
        haveFirstIsThenNotThenNull(isNullPredicateCtx)
          || haveExprNotIsNullInsideWhere(isNullPredicateCtx));
  }

  private static boolean haveFirstIsThenNotThenNull(SDBLParser.IsNullPredicateContext isNullPredicateContext) {
    return isNullPredicateContext.NOT() != null;
  }

  private static boolean haveExprNotIsNullInsideWhere(SDBLParser.IsNullPredicateContext isNullPredicateCtx) {
    final var parent = (SDBLParser.PredicateContext) isNullPredicateCtx.getParent();
    if (parent.getChildCount() == NOT_IS_NULL_EXPR_MEMBER_COUNT && parent.NOT() != null) {
      return true;
    }
    return haveExprNotWithParens(parent);
  }

  private static boolean isTerminalNodeNOT(ParseTree node) {
    return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == SDBLParser.NOT;
  }

  private static boolean haveExprNotWithParens(SDBLParser.PredicateContext ctx) {
    final var rootCtx = Trees.getRootParent(ctx, RULES_OF_PARENT_FOR_SEARCH_CONDITION);
    if (rootCtx == null || rootCtx.getRuleIndex() == SDBLParser.RULE_query) {
      return false;
    }
    return rootCtx.getChildCount() == NOT_WITH_PARENS_EXPR_MEMBERS_COUNT && isTerminalNodeNOT(rootCtx.getChild(0));
  }

  private void checkSelect(String tableName, SDBLParser.SelectedFieldsContext columns) {
    checkStatements(tableName, columns, SELECT_STATEMENTS, EXCLUDED_TOP_RULE_FOR_SELECT, true);
  }

  private void checkStatements(String tableName, ParserRuleContext expression, Collection<Integer> statements,
                               Integer rootForStatement, boolean checkIsNullOperator) {

    Trees.findAllRuleNodes(expression, SDBLParser.RULE_column).stream()
      .filter(Objects::nonNull)
      .filter(SDBLParser.ColumnContext.class::isInstance)
      .map(SDBLParser.ColumnContext.class::cast)
      .filter(columnContext -> checkColumn(tableName, columnContext, statements, rootForStatement, checkIsNullOperator))
      .collect(Collectors.toCollection(() -> nodesForIssues));
  }

  private static boolean checkColumn(String tableName, SDBLParser.ColumnContext columnCtx,
                                     Collection<Integer> statements, Integer rootForStatement,
                                     boolean checkIsNullOperator) {
    return Optional.of(columnCtx)
      .filter(columnContext -> columnContext.mdoName != null)
      .filter(columnContext -> columnContext.mdoName.getText().equalsIgnoreCase(tableName))
      .filter(columnContext -> !haveIsNullInsideExprForColumn(columnContext, statements, rootForStatement,
        checkIsNullOperator))
      .isPresent();
  }

  private static boolean haveIsNullInsideExprForColumn(ParserRuleContext ctx, Collection<Integer> statements,
                                                       Integer rootForStatement, boolean checkIsNullOperator) {
    var selectStatement = Trees.getRootParent(ctx, statements);
    if (selectStatement == null || selectStatement.getChildCount() == 0
      || rootForStatement == selectStatement.getRuleIndex()) {
      return false;
    }
    if (checkIsNullOperator && haveIsNullOperator(selectStatement)) {
      return true;
    }
    return haveIsNullFunction(selectStatement)
      || haveIsNullInsideExprForColumn(selectStatement, statements, rootForStatement, checkIsNullOperator);
  }

  private static boolean haveIsNullOperator(ParserRuleContext ctx) {
    return Optional.of(ctx)
      .filter(SDBLParser.IsNullPredicateContext.class::isInstance)
      .map(SDBLParser.IsNullPredicateContext.class::cast)
      .isPresent();
  }

  private static boolean haveIsNullFunction(ParserRuleContext ctx) {
    return Optional.of(ctx)
      .filter(SDBLParser.BuiltInFunctionsContext.class::isInstance)
      .map(SDBLParser.BuiltInFunctionsContext.class::cast)
      .map(SDBLParser.BuiltInFunctionsContext::ISNULL)
      .isPresent();
  }

  private void checkWhere(String tableName, @Nullable SDBLParser.LogicalExpressionContext where) {
    Optional.ofNullable(where)
      .stream().flatMap(searchConditionsContext -> searchConditionsContext.condidions.stream())
      .forEach(searchConditionContext -> checkStatements(tableName, searchConditionContext,
        WHERE_STATEMENTS, EXCLUDED_TOP_RULE_FOR_WHERE, true));
  }

  private void checkAllJoins(String tableName, SDBLParser.JoinPartContext currentJoinPart) {
    Optional.ofNullable(Trees.getRootParent(currentJoinPart, SDBLParser.RULE_dataSource))
      .filter(SDBLParser.DataSourceContext.class::isInstance)
      .stream().flatMap(ctx -> ((SDBLParser.DataSourceContext) ctx).joinPart().stream())
      .filter(joinPartContext -> joinPartContext != currentJoinPart)
      .map(SDBLParser.JoinPartContext::logicalExpression)
      .forEach(searchConditionsContext -> checkStatements(tableName, searchConditionsContext,
        JOIN_STATEMENTS, EXCLUDED_TOP_RULE_FOR_JOIN, false));
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
