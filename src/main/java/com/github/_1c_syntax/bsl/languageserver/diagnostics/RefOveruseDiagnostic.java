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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

  private static final String REF_REGEX = "Ссылка|Reference";
  private static final Pattern REF_PATTERN = CaseInsensitivePattern.compile(REF_REGEX);
  private static final int BAD_CHILD_COUNT = 3;
  private Collection<ParseTree> dataSourceCollection = new ArrayList<>();

  @Override
  public ParseTree visitQuery(SDBLParser.QueryContext ctx) {
    var columnsCollection = Trees.findAllRuleNodes(ctx, SDBLParser.RULE_column);

    if (columnsCollection.isEmpty()
      || dataSourceCollection.stream().anyMatch(Trees::treeContainsErrors)) {
      return ctx;
    }

    if (dataSourceCollection.isEmpty()) {
      performSimpleCheck(columnsCollection);
      return ctx;
    }

    var tableNames = dataSourceCollection.stream()
      .map(RefOveruseDiagnostic::getTableNameOrAlias)
      .collect(Collectors.toSet());

    columnsCollection.forEach(column -> checkColumnNode((SDBLParser.ColumnContext) column, tableNames));
    return ctx;

  }

  @Override
  public ParseTree visitSelectQuery(SDBLParser.SelectQueryContext ctx) {
    this.dataSourceCollection = Trees.findAllRuleNodes(ctx, SDBLParser.RULE_dataSource);
    return super.visitSelectQuery(ctx);
  }

  private void performSimpleCheck(Collection<ParseTree> columnsCollection) {
    columnsCollection.stream()
      .filter(columnNode -> columnNode.getChildCount() > BAD_CHILD_COUNT)
      .map(column -> column.getChild(column.getChildCount() - 1))
      .filter(lastChild -> REF_PATTERN.matcher(lastChild.getText()).matches())
      .forEach(node -> diagnosticStorage.addDiagnostic((BSLParserRuleContext) node));
  }

  private void checkColumnNode(SDBLParser.ColumnContext ctx, Set<String> tableNames) {

    if (ctx.children == null) {
      return;
    }

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

    if (childCount < 3) {
      return;
    }

    var lastChild = ctx.getChild(childCount - 1);
    // dots are also children of ColumnContext,
    // that is why -3 must be an index of penultimate identifier
    var penultimateChild = ctx.getChild(childCount - 3);

    String lastIdentifierName = lastChild.getText();
    String penultimateIdentifierName = penultimateChild.getText();

    if (REF_PATTERN.matcher(penultimateIdentifierName).matches()
      && !penultimateIdentifierParentIsTabularSection(ctx)
      || (REF_PATTERN.matcher(lastIdentifierName).matches()
      && !tableNames.contains(penultimateIdentifierName))) {
      diagnosticStorage.addDiagnostic(ctx);
    }
  }

  private boolean penultimateIdentifierParentIsTabularSection(SDBLParser.ColumnContext ctx) {
    var penultimateChildTable = ctx.getChild(0);
    var penultimateChildTableName = penultimateChildTable.getText();

    return this.dataSourceCollection.stream()
      .filter(dataSource -> dataSource.getChild(0).getChildCount() > 2)
      .anyMatch(dataSource -> dataSource.getChild(1).getChild(1).getText().matches(penultimateChildTableName));
  }

  private static String getTableNameOrAlias(ParseTree dataSource) {
    return Optional.of(dataSource)
      .flatMap(dataSrc -> extractTextFromChild(dataSrc, SDBLParser.RULE_alias))
      .or(() -> Optional.of(dataSource)
        .flatMap(dataSrc -> extractTextFromChild(dataSrc, SDBLParser.RULE_table)))
      .or(() -> Optional.of(dataSource)
        .flatMap(dataSrc -> extractTextFromChild(dataSrc, SDBLParser.RULE_parameterTable)))
      .orElse("");
  }

  private static Optional<String> extractTextFromChild(ParseTree parseTree, int childRuleType) {
    return Optional.of(parseTree)
      .flatMap(tree -> Trees.getFirstChild(tree, childRuleType))
      .flatMap(child -> Trees.getFirstChild(child, SDBLParser.RULE_identifier))
      .map(BSLParserRuleContext::getText);
  }
}
