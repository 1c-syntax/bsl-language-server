/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.apache.commons.lang3.StringUtils;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5
)
public class DeletingCollectionItemDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern deletePattern = Pattern.compile(
    "(удалить|delete)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {

    String collectionName = ctx.expression().getText();

    Collection<ParseTree> childStatements = Trees.findAllRuleNodes(ctx, BSLParser.RULE_accessCall)
      .stream()
      .filter(node -> deletePattern.matcher(
        ((BSLParser.AccessCallContext) node).methodCall().methodName().getText()).matches()
      ).collect(Collectors.toList());

    for (ParseTree node : childStatements) {

      ArrayList<ParseTree> statementsParts = new ArrayList<>();
      ParseTree callStatement = node.getParent();
      statementsParts.add(((BSLParser.CallStatementContext) callStatement).IDENTIFIER());
      statementsParts.add(((BSLParser.CallStatementContext) callStatement).globalMethodCall());

      if (((BSLParser.CallStatementContext) callStatement).modifier() != null) {
        statementsParts.addAll(((BSLParser.CallStatementContext) callStatement).modifier());
      }

      List<String> deleteCollection = statementsParts.stream()
        .filter(Objects::nonNull)
        .map(ParseTree::getText).collect(Collectors.toList());

      String deleteCollectionName = StringUtils.join(deleteCollection, "");

      if (!deleteCollectionName.equalsIgnoreCase(collectionName)) {
        continue;
      }

      diagnosticStorage.addDiagnostic((BSLParserRuleContext) callStatement, getDiagnosticMessage());
    }

    return super.visitForEachStatement(ctx);

  }
}
