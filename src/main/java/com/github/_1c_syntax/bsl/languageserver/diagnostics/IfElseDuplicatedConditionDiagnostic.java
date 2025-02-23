/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import jakarta.annotation.PostConstruct;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Leon Chagelishvili &lt;lChagelishvily@gmail.com&gt;
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)
public class IfElseDuplicatedConditionDiagnostic extends AbstractVisitorDiagnostic {

  private String relatedMessage;
  private final Set<BSLParser.ExpressionContext> checkedConditions = new HashSet<>();

  @PostConstruct
  public void init() {
    relatedMessage = this.info.getResourceString("identicalConditionRelatedMessage");
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    checkedConditions.clear();

    List<BSLParser.ExpressionContext> expressions = new ArrayList<>();

    expressions.add(ctx.ifBranch().expression());

    ctx.elsifBranch().stream()
      .map(BSLParser.ElsifBranchContext::expression)
      .collect(Collectors.toCollection(() -> expressions));

    findDuplicatedExpression(expressions);
    return super.visitIfStatement(ctx);
  }

  private void findDuplicatedExpression(List<BSLParser.ExpressionContext> expressionContexts) {
    for (int i = 0; i < expressionContexts.size() - 1; i++) {
      if (!checkedConditions.contains(expressionContexts.get(i))) {
        checkExpression(expressionContexts, i);
      }
    }
  }

  private void checkExpression(List<BSLParser.ExpressionContext> expressionContexts, int i) {
    var currentExpression = expressionContexts.get(i);

    var identicalExpressions = expressionContexts.stream()
      .skip(i)
      .filter(expressionContext ->
        !expressionContext.equals(currentExpression)
          && DiagnosticHelper.equalNodes(currentExpression, expressionContext))
      .toList();

    if (identicalExpressions.isEmpty()) {
      return;
    }

    identicalExpressions.stream().collect(Collectors.toCollection(() -> checkedConditions));
    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

    relatedInformation.add(RelatedInformation.create(
      documentContext.getUri(),
      Ranges.create(currentExpression),
      relatedMessage
    ));

    identicalExpressions.stream()
      .map(expressionContext ->
        RelatedInformation.create(
          documentContext.getUri(),
          Ranges.create(expressionContext),
          relatedMessage
        )
      )
      .collect(Collectors.toCollection(() -> relatedInformation));

    diagnosticStorage.addDiagnostic(currentExpression, relatedInformation);
  }
}
