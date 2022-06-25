/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLParser.DataSourceContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.PERFORMANCE
  }
)
public class RefOveruseDiagnostic extends AbstractSDBLVisitorDiagnostic {

  private static final Pattern REF_PATTERN = CaseInsensitivePattern.compile("Ссылка|Reference");
  private static final int BAD_CHILD_COUNT = 3;
  private static final int COUNT_OF_TABLE_DOT_REF_DOT_REF = 5;
  private static final Set<Integer> RULE_COLUMNS = Set.of(SDBLParser.RULE_column, SDBLParser.RULE_query);
  private Map<String, Boolean> dataSourcesWithTabularFlag = Collections.emptyMap();
  private Map<String, Boolean> prevDataSourcesWithTabularFlag = Collections.emptyMap();
  @Nullable private Range prevQueryRange;

  @Override
  public ParseTree visitQueryPackage(SDBLParser.QueryPackageContext ctx) {
    var result = super.visitQueryPackage(ctx);
    prevQueryRange = null;
    prevDataSourcesWithTabularFlag = Collections.emptyMap();
    dataSourcesWithTabularFlag = Collections.emptyMap();
    return result;
  }

  @Override
  public ParseTree visitQuery(SDBLParser.QueryContext ctx) {
    checkQuery(ctx).forEach(diagnosticStorage::addDiagnostic);
    return super.visitQuery(ctx);
  }

  private Stream<BSLParserRuleContext> checkQuery(SDBLParser.QueryContext ctx) {
    var columns = Trees.findAllTopLevelRuleNodes(ctx, RULE_COLUMNS).stream()
      .filter(parserRuleContext -> parserRuleContext.getRuleIndex() == SDBLParser.RULE_column)
      .collect(Collectors.toList());

    if (columns.isEmpty()) {
      return Stream.empty();
    }

    dataSourcesWithTabularFlag = dataSourcesWithTabularSection(ctx);
    if (dataSourcesWithTabularFlag.isEmpty()) {
      return getSimpleOverused(columns);
    }

    return getOverused(columns);
  }

  private Map<String, Boolean> dataSourcesWithTabularSection(SDBLParser.QueryContext ctx) {
    var newResult = findAllDataSourceWithoutInnerQueries(ctx)
      .collect(Collectors.toMap(
        RefOveruseDiagnostic::getTableNameOrAlias,
        RefOveruseDiagnostic::isTableWithTabularSection,
        (existing, replacement) -> existing,
        HashMap::new));

    var queryRange = Ranges.create(ctx);

    final Map<String, Boolean> result;
    if (prevQueryRange == null || !Ranges.containsRange(prevQueryRange, queryRange)){
      result = newResult;
      prevDataSourcesWithTabularFlag = result;
      prevQueryRange = queryRange;
    } else {
      result = new HashMap<>(newResult);
      result.putAll(prevDataSourcesWithTabularFlag);
    }
    return result;
  }

  private static Stream<? extends DataSourceContext> findAllDataSourceWithoutInnerQueries(
    SDBLParser.QueryContext ctx) {
    if (ctx.from == null){
      return Stream.empty();
    }
    return Stream.concat(
      ctx.from.dataSource().stream(),
      ctx.from.dataSource().stream()
        .flatMap(dataSourceContext -> getInnerDataSource(dataSourceContext).stream())
    );
  }

  private static Collection<DataSourceContext> getInnerDataSource(DataSourceContext dataSourceContext) {
    var result = new ArrayList<DataSourceContext>();
    Optional.ofNullable(dataSourceContext.dataSource())
        .map(RefOveruseDiagnostic::getInnerDataSource)
        .ifPresent(result::addAll);

    var joinDataSources = dataSourceContext.joinPart().stream()
      .map(SDBLParser.JoinPartContext::dataSource)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    result.addAll(joinDataSources);

    var dataSourcesFromJoins = joinDataSources.stream()
      .flatMap(dataSourceContext1 -> getInnerDataSource(dataSourceContext1).stream())
      .collect(Collectors.toList());

    result.addAll(dataSourcesFromJoins);
    return result;
  }


  private static String getTableNameOrAlias(DataSourceContext dataSource) {
    final var value = Optional.of(dataSource);
    return value
      .map(DataSourceContext::alias)
      .map(alias -> (ParseTree)alias.name)
      .or(() -> value
        .map(DataSourceContext::table)
        .map(tableContext -> (ParseTree)tableContext.tableName))
      .or(() -> value
        .map(DataSourceContext::parameterTable)
        .map(tableContext -> (ParseTree)tableContext.parameter()))
      .map(ParseTree::getText)
      .orElse("");
  }

  private static boolean isTableWithTabularSection(DataSourceContext dataSourceContext) {
    final var table = dataSourceContext.table();
    if (table == null) {
      return dataSourceContext.virtualTable() != null;
    }
    return table.tableName != null || table.objectTableName != null;
  }

  private static Stream<BSLParserRuleContext> getSimpleOverused(List<ParserRuleContext> columnsCollection) {
    return columnsCollection.stream()
      .filter(columnNode -> columnNode.getChildCount() > BAD_CHILD_COUNT)
      .map(column -> column.getChild(column.getChildCount() - 1))
      .filter(lastChild -> REF_PATTERN.matcher(lastChild.getText()).matches())
      .map(BSLParserRuleContext.class::cast);
  }

  private Stream<BSLParserRuleContext> getOverused(List<ParserRuleContext> columnsCollection) {
    return columnsCollection.stream()
      .map(SDBLParser.ColumnContext.class::cast)
      .filter(column -> column.getChildCount() >= BAD_CHILD_COUNT)
      .filter(this::isOveruse)
      .map(BSLParserRuleContext.class::cast);
  }

  private boolean isOveruse(SDBLParser.ColumnContext ctx) {

    // children:
    //
    // Контрагент.Ссылка.ЮрФизЛицо
    //     ^     ^   ^  ^    ^
    //     0     1   2  3    4
    //
    // Контрагент.ЮрФизЛицо
    //     ^     ^    ^
    //     0     1    2

    final int childCount = ctx.children.size();

    // dots are also children of ColumnContext,
    // that is why -3 must be an index of penultimate identifier
    var penultimateChild = ctx.getChild(childCount - BAD_CHILD_COUNT);

    var penultimateIdentifierName = penultimateChild.getText();

    if (REF_PATTERN.matcher(penultimateIdentifierName).matches()) {
      if (childCount < COUNT_OF_TABLE_DOT_REF_DOT_REF){
        return true;
      }
      var prevChildID = ctx.getChild(childCount - COUNT_OF_TABLE_DOT_REF_DOT_REF).getText();
      return !dataSourcesWithTabularFlag.getOrDefault(prevChildID, false);
    }
    var lastIdentifierName = ctx.getChild(childCount - 1).getText();
    if (REF_PATTERN.matcher(lastIdentifierName).matches()) {
      return dataSourcesWithTabularFlag.get(penultimateIdentifierName) == null;
    }
    return false;
  }
}
