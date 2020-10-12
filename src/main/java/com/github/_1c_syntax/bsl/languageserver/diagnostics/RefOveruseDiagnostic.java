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
import java.util.Optional;
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
  private static final Pattern PATTERN = CaseInsensitivePattern.compile(REF_REGEX);

  @Override
  public ParseTree visitQuery(SDBLParser.QueryContext ctx) {
    var dataSource = Trees.findAllRuleNodes(ctx, SDBLParser.RULE_dataSource);
    var columnsCollection = Trees.findAllRuleNodes(ctx, SDBLParser.RULE_column);
    if (dataSource.isEmpty() || columnsCollection.isEmpty()) {
      return super.visitQuery(ctx);
    }

    String tableName = getTableNameOrAlias(dataSource);
    columnsCollection.forEach(column -> checkColumnNode((SDBLParser.ColumnContext) column, tableName));
    return super.visitQuery(ctx);

  }

  private void checkColumnNode(SDBLParser.ColumnContext ctx, String tableName) {

    if (ctx.children == null) {
      return;
    }

    var lastChild = ctx.getChild(Math.min(ctx.children.size(), ctx.children.size() - 1));
    var penultimateChild = ctx.getChild(Math.min(ctx.children.size(), ctx.children.size() - 3));

    if (lastChild != penultimateChild
      && lastChild instanceof SDBLParser.IdentifierContext
      && penultimateChild instanceof SDBLParser.IdentifierContext) {

      performCheck(ctx, (SDBLParser.IdentifierContext) lastChild,
        (SDBLParser.IdentifierContext) penultimateChild, tableName);
    }

  }

  private void performCheck(SDBLParser.ColumnContext ctx, SDBLParser.IdentifierContext lastChild,
                            SDBLParser.IdentifierContext penultimateChild, String tableName) {

    if (!penultimateChild.getText().equals(tableName) &&
      PATTERN.matcher(lastChild.getText()).matches()) {
      diagnosticStorage.addDiagnostic(ctx);
    }
  }

  private String getTableNameOrAlias(Collection<ParseTree> dataSourceCollection) {

    String alias = dataSourceCollection.stream()
      .map(dataSource -> Trees.getFirstChild(dataSource, SDBLParser.RULE_alias))
      .filter(Optional::isPresent)
      .map(optionalAlias -> Trees.getFirstChild(optionalAlias.get(), SDBLParser.RULE_identifier))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(BSLParserRuleContext::getText)
      .collect(Collectors.joining());

    if (!alias.isBlank()) {
      return alias;
    }

    return dataSourceCollection.stream()
      .map(dataSource -> Trees.getFirstChild(dataSource, SDBLParser.RULE_table))
      .filter(Optional::isPresent)
      .map(optionalAlias -> Trees.getFirstChild(optionalAlias.get(), SDBLParser.RULE_identifier))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(BSLParserRuleContext::getText)
      .collect(Collectors.joining());
  }

}
