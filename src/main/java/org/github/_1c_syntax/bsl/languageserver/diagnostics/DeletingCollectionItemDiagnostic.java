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
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

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
  private String collectionName;

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {

    collectionName = ctx.expression().getText();

    Trees.findAllRuleNodes(ctx, BSLParser.RULE_accessCall)
      .stream()
      .filter(Objects::nonNull)
      .filter(node -> deletePattern.matcher(
        ((BSLParser.AccessCallContext) node).methodCall().methodName().getText()).matches()
      )
      .map(ParseTree::getParent)
      .filter(callStatement -> collectionNamesEqual(((BSLParser.CallStatementContext) callStatement)))
      .forEach(callStatement -> diagnosticStorage.addDiagnostic(
        ((BSLParserRuleContext) callStatement), getDiagnosticMessage(collectionName)
      ));

    return super.visitForEachStatement(ctx);

  }

  private boolean collectionNamesEqual(BSLParser.CallStatementContext node) {

    boolean isEqual = false;

    String callStatementText = node.getText().toLowerCase(Locale.getDefault());
    String methodText = node.accessCall().methodCall().getText().toLowerCase(Locale.getDefault());

    String deleteCollectionName = callStatementText.replace( "." + methodText, "");
    if (deleteCollectionName.equalsIgnoreCase(collectionName)) {
      isEqual = true;
    }

    return isEqual;
  }

}
