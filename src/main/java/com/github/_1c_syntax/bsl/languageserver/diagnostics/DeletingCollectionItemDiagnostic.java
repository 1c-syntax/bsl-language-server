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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.ERROR
  }
)
public class DeletingCollectionItemDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern deletePattern = Pattern.compile(
    "(удалить|delete)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public DeletingCollectionItemDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {

    String expression = ctx.expression().getText().toLowerCase(Locale.getDefault());

    Trees.findAllRuleNodes(ctx.codeBlock(), BSLParser.RULE_methodCall)
      .stream()
      .filter(node -> deletePattern.matcher(
        ((BSLParser.MethodCallContext) node).methodName().getText()).matches()
      )
      .map(node -> node.getParent().getParent())
      .filter(callStatement -> namesEqual(callStatement, expression))
      .forEach(callStatement -> diagnosticStorage.addDiagnostic(
          (BSLParser.CallStatementContext) callStatement, info.getDiagnosticMessage(expression))
      );

    return super.visitForEachStatement(ctx);

  }

  private static boolean namesEqual(ParseTree node, String expression) {

    if (!(node instanceof BSLParser.CallStatementContext)) {
      return false;
    }

    BSLParser.CallStatementContext callStatement = (BSLParser.CallStatementContext) node;

    String callStatementText = callStatement.getText().toLowerCase(Locale.getDefault());
    String prefix = expression
      + "."
      + callStatement.accessCall().methodCall().methodName().getText().toLowerCase(Locale.getDefault())
      + "(";
    return callStatementText.startsWith(prefix);

  }

}
