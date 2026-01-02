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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.CallStatementContext;
import com.github._1c_syntax.bsl.parser.BSLParser.MethodCallContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;
import java.util.function.Predicate;
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

  private static final Pattern DELETE_CALL_PATTERN = CaseInsensitivePattern.compile(
    "(удалить|delete)"
  );
  private static final Predicate<MethodCallContext> MATCH_METHOD_CALL_DELETE
    = e -> DELETE_CALL_PATTERN.matcher(e.methodName().getText()).matches();

  private static boolean namesEqual(CallStatementContext callStatement, String collectionExpression) {

    String callStatementText = callStatement.getText().toLowerCase(Locale.getDefault());
    String prefix = collectionExpression.toLowerCase(Locale.getDefault())
      + "."
      + callStatement.accessCall().methodCall().methodName().getText().toLowerCase(Locale.getDefault())
      + "(";
    return callStatementText.startsWith(prefix);

  }

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {

    String collectionExpression = ctx.expression().getText();
    Trees.findAllRuleNodes(ctx.codeBlock(), BSLParser.RULE_methodCall)
      .stream()
      .filter(MethodCallContext.class::isInstance)
      .map(MethodCallContext.class::cast)
      .filter(MATCH_METHOD_CALL_DELETE)
      .map(node -> node.getParent().getParent())
      .filter(CallStatementContext.class::isInstance)
      .map(CallStatementContext.class::cast)
      .filter(callStatement -> namesEqual(callStatement, collectionExpression))
      .forEach(callStatement -> diagnosticStorage.addDiagnostic(
        callStatement, info.getMessage(collectionExpression))
      );

    return super.visitForEachStatement(ctx);

  }
}
