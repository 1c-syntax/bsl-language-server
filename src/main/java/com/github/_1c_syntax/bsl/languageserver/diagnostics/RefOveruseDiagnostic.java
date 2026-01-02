/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
import com.github._1c_syntax.bsl.mdo.TabularSection;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;

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
  private static final int COUNT_OF_TABLE_DOT_REF = 3;
  private static final int LAST_INDEX_OF_TABLE_DOT_REF = COUNT_OF_TABLE_DOT_REF - 1;
  private static final int COUNT_OF_TABLE_DOT_REF_DOT_REF = 5;
  private static final Set<Integer> RULE_COLUMNS = Set.of(SDBLParser.RULE_column, SDBLParser.RULE_query);
  private static final Set<Integer> METADATA_TYPES = Set.of(
    SDBLParser.BUSINESS_PROCESS_TYPE,
    SDBLParser.CATALOG_TYPE,
    SDBLParser.DOCUMENT_TYPE,
    SDBLParser.INFORMATION_REGISTER_TYPE,
    SDBLParser.CONSTANT_TYPE,
    SDBLParser.FILTER_CRITERION_TYPE,
    SDBLParser.EXCHANGE_PLAN_TYPE,
    SDBLParser.SEQUENCE_TYPE,
    SDBLParser.DOCUMENT_JOURNAL_TYPE,
    SDBLParser.ENUM_TYPE,
    SDBLParser.CHART_OF_CHARACTERISTIC_TYPES_TYPE,
    SDBLParser.CHART_OF_ACCOUNTS_TYPE,
    SDBLParser.CHART_OF_CALCULATION_TYPES_TYPE,
    SDBLParser.ACCUMULATION_REGISTER_TYPE,
    SDBLParser.ACCOUNTING_REGISTER_TYPE,
    SDBLParser.CALCULATION_REGISTER_TYPE,
    SDBLParser.TASK_TYPE,
    SDBLParser.EXTERNAL_DATA_SOURCE_TYPE);
  private static final Collection<Integer> EXCLUDED_COLUMNS_ROOT =
    Set.of(SDBLParser.RULE_inlineTableField, SDBLParser.RULE_query);
  public static final List<String> SPECIAL_LIST_FOR_DATA_SOURCE = List.of("");

  private Map<String, List<String>> dataSourceWithTabularSectionNames = Collections.emptyMap();
  private Map<String, List<String>> prevDataSourceWithTabularSectionNames = Collections.emptyMap();
  @Nullable
  private Range prevQueryRange;

  @Override
  public ParseTree visitQueryPackage(SDBLParser.QueryPackageContext ctx) {
    var result = super.visitQueryPackage(ctx);
    prevQueryRange = null;
    prevDataSourceWithTabularSectionNames = Collections.emptyMap();
    dataSourceWithTabularSectionNames = Collections.emptyMap();
    return result;
  }

  @Override
  public ParseTree visitQuery(SDBLParser.QueryContext ctx) {
    checkQuery(ctx).forEach(diagnosticStorage::addDiagnostic);
    return super.visitQuery(ctx);
  }

  private Stream<SDBLParser.ColumnContext> checkQuery(SDBLParser.QueryContext ctx) {
    var columns = Trees.findAllTopLevelDescendantNodes(ctx, RULE_COLUMNS).stream()
      .filter(parserRuleContext -> parserRuleContext.getRuleIndex() == SDBLParser.RULE_column)
      .filter(parserRuleContext ->
        Optional.ofNullable(Trees.getRootParent(parserRuleContext, EXCLUDED_COLUMNS_ROOT))
          .map(root -> root.getRuleIndex() == SDBLParser.RULE_query)
          .orElse(false)
      )
      .map(SDBLParser.ColumnContext.class::cast)
      .collect(Collectors.toList());

    if (columns.isEmpty()) {
      return Stream.empty();
    }

    dataSourceWithTabularSectionNames = dataSourcesWithTabularSection(ctx);
    if (dataSourceWithTabularSectionNames.isEmpty()) {
      return getSimpleOverused(columns);
    }

    return getOverused(columns);
  }

  private Map<String, List<String>> dataSourcesWithTabularSection(SDBLParser.QueryContext ctx) {
    var newResult = calcDataSourceWithTabularSectionNames(findAllDataSourceWithoutInnerQueries(ctx));

    var queryRange = Ranges.create(ctx);

    final Map<String, List<String>> result;
    if (prevQueryRange == null || !Ranges.containsRange(prevQueryRange, queryRange)) {
      result = newResult;
      prevDataSourceWithTabularSectionNames = result;
      prevQueryRange = queryRange;
    } else {
      result = new HashMap<>(newResult);
      result.putAll(prevDataSourceWithTabularSectionNames);
    }
    return result;
  }

  private Map<String, List<String>> calcDataSourceWithTabularSectionNames(
    Stream<? extends SDBLParser.DataSourceContext> dataSources
  ) {

    return dataSources
      .map(dataSourceContext -> new TabularSectionTable(getTableNameOrAlias(dataSourceContext),
        getTabularSectionNames(dataSourceContext)))
      .collect(Collectors.toMap(
        TabularSectionTable::tableNameOrAlias,
        TabularSectionTable::tabularSectionNames,
        (existing, replacement) -> existing));
  }

  private static Stream<? extends SDBLParser.DataSourceContext> findAllDataSourceWithoutInnerQueries(
    SDBLParser.QueryContext ctx) {
    if (ctx.from == null) {
      return Stream.empty();
    }
    return Stream.concat(
      ctx.from.dataSource().stream(),
      ctx.from.dataSource().stream()
        .flatMap(dataSourceContext -> getInnerDataSource(dataSourceContext).stream())
    );
  }

  private static Collection<SDBLParser.DataSourceContext> getInnerDataSource(
    SDBLParser.DataSourceContext dataSourceContext
  ) {
    var result = new ArrayList<SDBLParser.DataSourceContext>();
    Optional.ofNullable(dataSourceContext.dataSource())
      .map(RefOveruseDiagnostic::getInnerDataSource)
      .ifPresent(result::addAll);

    var joinDataSources = dataSourceContext.joinPart().stream()
      .map(SDBLParser.JoinPartContext::dataSource)
      .filter(Objects::nonNull)
      .toList();
    result.addAll(joinDataSources);

    var dataSourcesFromJoins = joinDataSources.stream()
      .flatMap(dataSourceContext1 -> getInnerDataSource(dataSourceContext1).stream())
      .toList();

    result.addAll(dataSourcesFromJoins);
    return result;
  }

  private static String getTableNameOrAlias(SDBLParser.DataSourceContext dataSource) {
    final var value = Optional.of(dataSource);
    return value
      .map(SDBLParser.DataSourceContext::alias)
      .map(alias -> (ParseTree) alias.name)
      .or(() -> value
        .map(SDBLParser.DataSourceContext::table)
        .map(tableContext -> (ParseTree) tableContext.tableName))
      .or(() -> value
        .map(SDBLParser.DataSourceContext::parameterTable)
        .map(tableContext -> (ParseTree) tableContext.parameter()))
      .map(ParseTree::getText)
      .orElse("");
  }

  private List<String> getTabularSectionNames(SDBLParser.DataSourceContext dataSourceContext) {
    final var table = dataSourceContext.table();
    if (table == null) {
      return getSpecialListForDataSource(dataSourceContext.virtualTable() != null);
    }
    final var mdo = dataSourceContext.table().mdo();
    if (mdo == null) {
      return getSpecialListForDataSource(table.tableName != null);
    }
    if (table.objectTableName != null) {
      return SPECIAL_LIST_FOR_DATA_SOURCE;
    }
    return getTabularSectionNames(mdo);
  }

  private static List<String> getSpecialListForDataSource(boolean useSpecialName) {
    if (useSpecialName) {
      return SPECIAL_LIST_FOR_DATA_SOURCE;
    }
    return Collections.emptyList();
  }

  private List<String> getTabularSectionNames(SDBLParser.MdoContext mdo) {
    final var configuration = documentContext.getServerContext()
      .getConfiguration();
    if (configuration.getConfigurationSource() == ConfigurationSource.EMPTY) {
      return Collections.emptyList();
    }
    return MDOType.fromValue(mdo.type.getText()).stream()
      .map(mdoTypeTabular -> MdoReference.create(mdoTypeTabular, mdo.tableName.getText()))
      .map(configuration::findChild)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .filter(TabularSectionOwner.class::isInstance)
      .map(TabularSectionOwner.class::cast)
      .flatMap(RefOveruseDiagnostic::getTabularSectionNames)
      .collect(Collectors.toList());
  }

  private static Stream<String> getTabularSectionNames(TabularSectionOwner tabularSectionOwner) {
    return tabularSectionOwner.getTabularSections().stream()
      .map(TabularSection::getName);
  }

  private static Stream<SDBLParser.ColumnContext> getSimpleOverused(List<SDBLParser.ColumnContext> columnsCollection) {
    return columnsCollection.stream()
      .filter(columnNode -> columnNode.getChildCount() > COUNT_OF_TABLE_DOT_REF)
      .filter(column -> REF_PATTERN.matcher(column.getChild(column.getChildCount() - 1).getText()).matches());
  }

  private Stream<SDBLParser.ColumnContext> getOverused(List<SDBLParser.ColumnContext> columnsCollection) {
    return columnsCollection.stream()
      .filter(column -> column.getChildCount() >= COUNT_OF_TABLE_DOT_REF)
      .filter(this::isOveruse);
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

    var children = extractFirstMetadataTypeName(ctx);
    var refIndex = findLastRef(children);

    final var lastIndex = children.size() - 1;
    if (refIndex == lastIndex) {
      var penultimateIdentifierName = children.get(lastIndex - LAST_INDEX_OF_TABLE_DOT_REF).getText();
      return dataSourceWithTabularSectionNames.get(penultimateIdentifierName) == null;
    }
    if (refIndex < LAST_INDEX_OF_TABLE_DOT_REF) {
      return false;
    }
    if (refIndex > LAST_INDEX_OF_TABLE_DOT_REF) {
      return true;
    }
    var tabName = children.get(0).getText();
    return dataSourceWithTabularSectionNames.getOrDefault(tabName, Collections.emptyList()).isEmpty();
  }

  private static int findLastRef(List<ParseTree> children) {
    for (int i = children.size() - 1; i >= 0; i--) {
      final var child = children.get(i);
      final var childText = child.getText();
      if (REF_PATTERN.matcher(childText).matches()) {
        return i;
      }
    }
    return -1;
  }

  private static List<ParseTree> extractFirstMetadataTypeName(SDBLParser.ColumnContext ctx) {
    final var mdoName = ctx.mdoName;
    final var children = ctx.children;
    if (mdoName == null || children.size() < COUNT_OF_TABLE_DOT_REF_DOT_REF
      || !METADATA_TYPES.contains(mdoName.getStart().getType())) {
      return children;
    }
    return children.subList(1, children.size() - 1);
  }

  private record TabularSectionTable(String tableNameOrAlias, List<String> tabularSectionNames) {
  }
}
