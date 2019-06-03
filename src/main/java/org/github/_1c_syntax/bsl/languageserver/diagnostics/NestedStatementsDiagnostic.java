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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.ALL,
  minutesToFix = 30
)
public class NestedStatementsDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MAX_ALLOWED_NESTED = 3;
  private Collection<ParseTree> nestedParents = new ArrayList<>();

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    doDiagnostic(ctx);
    return super.visitIfStatement(ctx);
  }

  @Override
  public ParseTree visitWhileStatement(BSLParser.WhileStatementContext ctx) {
    doDiagnostic(ctx);
    return super.visitWhileStatement(ctx);
  }

  @Override
  public ParseTree visitForStatement(BSLParser.ForStatementContext ctx) {
    doDiagnostic(ctx);
    return super.visitForStatement(ctx);
  }

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
    doDiagnostic(ctx);
    return super.visitForEachStatement(ctx);
  }

  @Override
  public ParseTree visitTryStatement(BSLParser.TryStatementContext ctx) {
    doDiagnostic(ctx);
    return super.visitTryStatement(ctx);
  }

  private void doDiagnostic(BSLParserRuleContext ctx) {

    nestedParents.clear();
    reverseParent(ctx);

    if (nestedParents.size() >= MAX_ALLOWED_NESTED) {

      List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

      String relatedMessage = "sdddd";

      nestedParents.stream()
        .map(expressionContext ->
          this.createRelatedInformation(
            RangeHelper.newRange((BSLParserRuleContext) expressionContext),
            relatedMessage
          )
        )
        .collect(Collectors.toCollection(() -> relatedInformation));

      addDiagnostic(ctx, relatedInformation);

    }

  }

  private void reverseParent(BSLParserRuleContext ctx) {

    ParserRuleContext parent = ctx.getParent();
    if (parent instanceof BSLParser.IfStatementContext
      || parent instanceof BSLParser.WhileStatementContext
      || parent instanceof BSLParser.ForStatementContext
      || parent instanceof BSLParser.ForEachStatementContext
      || parent instanceof BSLParser.TryStatementContext
    ) {
      nestedParents.add(parent);
    }

    if (parent != null) {
      reverseParent((BSLParserRuleContext) parent);
    }
  }

}
