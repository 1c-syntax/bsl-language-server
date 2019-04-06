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
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Leon Chagelishvili <lChagelishvily@gmail.com>
 */
@DiagnosticMetadata(
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10
)
public class IfElseDuplicatedConditionDiagnostic extends AbstractVisitorDiagnostic {

  private final String relatedMessage = getResourceString("identicalConditionRelatedMessage");
  private Set<BSLParser.ExpressionContext> checkedConditions = new HashSet<>();

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    checkedConditions.clear();
    findDuplicatedExpression(ctx.expression());
    return super.visitIfStatement(ctx);
  }

  private void findDuplicatedExpression(List<BSLParser.ExpressionContext> expressionContexts) {
    for (int i = 0; i < expressionContexts.size() - 1; i++) {
      if (!checkedConditions.contains(expressionContexts.get(i)))
        checkExpression(expressionContexts, i);
    }
  }

  private void checkExpression(List<BSLParser.ExpressionContext> expressionContexts, int i) {
    BSLParser.ExpressionContext currentExpression = expressionContexts.get(i);

    List<BSLParser.ExpressionContext> identicalExpressions = expressionContexts.stream()
      .skip((long) i)
      .filter(expressionContext ->
        !expressionContext.equals(currentExpression)
          && DiagnosticHelper.equalNodes(currentExpression, expressionContext))
      .collect(Collectors.toList());

    if (identicalExpressions.isEmpty()) {
      return;
    }

    identicalExpressions.stream()
      .collect(Collectors.toCollection(() -> checkedConditions));

    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

    relatedInformation.add(this.createRelatedInformation(
      RangeHelper.newRange(currentExpression),
      relatedMessage
    ));

    identicalExpressions.stream()
      .map(expressionContext ->
        this.createRelatedInformation(
          RangeHelper.newRange(expressionContext),
          relatedMessage
        )
      )
      .collect(Collectors.toCollection(() -> relatedInformation));

    addDiagnostic(currentExpression, relatedInformation);
  }

}

