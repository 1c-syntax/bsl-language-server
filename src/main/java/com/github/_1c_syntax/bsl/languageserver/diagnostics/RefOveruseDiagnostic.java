/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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

  @Override
  public ParseTree visitQuery(SDBLParser.QueryContext ctx) {
    var dataSourceCollection = Trees.findAllRuleNodes(ctx, SDBLParser.RULE_dataSource);
    var columnsCollection = Trees.findAllRuleNodes(ctx, SDBLParser.RULE_column);

    if (columnsCollection.isEmpty()) {
      return ctx;
    }

    if (dataSourceCollection.isEmpty()) {
      performSimpleCheck(columnsCollection);
      return ctx;
    }

    Set<String> tableNames = new HashSet<>();
    for (var dataSource : dataSourceCollection) {
      tableNames.add(getTableNameOrAlias(dataSource));
    }
    columnsCollection.forEach(column -> checkColumnNode((SDBLParser.ColumnContext) column, tableNames));
    return ctx;

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

    var lastChild = ctx.getChild(Math.min(ctx.children.size(), ctx.children.size() - 1));
    var penultimateChild = ctx.getChild(Math.min(ctx.children.size(), ctx.children.size() - 3));

    if (lastChild != penultimateChild
      && lastChild instanceof SDBLParser.IdentifierContext
      && penultimateChild instanceof SDBLParser.IdentifierContext) {

      performCheck(ctx, (SDBLParser.IdentifierContext) lastChild,
        (SDBLParser.IdentifierContext) penultimateChild, tableNames);
    }

  }

  private void performCheck(SDBLParser.ColumnContext ctx, SDBLParser.IdentifierContext lastChild,
                            SDBLParser.IdentifierContext penultimateChild, Set<String> tableNames) {

    if (!tableNames.contains(penultimateChild.getText()) &&
      REF_PATTERN.matcher(lastChild.getText()).matches()) {
      diagnosticStorage.addDiagnostic(ctx);
    }
  }

  private String getTableNameOrAlias(ParseTree dataSource) {

    String alias = Optional.of(dataSource)
      .map(dataSrc -> Trees.getFirstChild(dataSrc, SDBLParser.RULE_alias))
      .filter(Optional::isPresent)
      .map(optionalAlias -> Trees.getFirstChild(optionalAlias.get(), SDBLParser.RULE_identifier))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(BSLParserRuleContext::getText)
      .orElse("");

    if (!alias.isBlank()) {
      return alias;
    }

    return Optional.of(dataSource)
      .map(dataSrc -> Trees.getFirstChild(dataSrc, SDBLParser.RULE_table))
      .filter(Optional::isPresent)
      .map(optionalAlias -> Trees.getFirstChild(optionalAlias.get(), SDBLParser.RULE_identifier))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(BSLParserRuleContext::getText)
      .orElse("");
  }

}
