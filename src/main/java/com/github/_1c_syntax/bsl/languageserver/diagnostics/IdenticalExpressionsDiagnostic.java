/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)
public class IdenticalExpressionsDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MIN_EXPRESSION_SIZE = 3;

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {

    List<? extends BSLParser.OperationContext> onlyOperation = ctx.operation();

    if (sufficientSize(ctx) || !isUniformExpression(onlyOperation)) {
      return super.visitChildren(ctx);
    }

    List<? extends BSLParser.MemberContext> onlyMembers = ctx.member();

    List<ParseTree> identicalExpressions = onlyMembers
      .stream()
      .filter((ParseTree t) -> onlyMembers
        .stream()
        .filter((ParseTree p) -> DiagnosticHelper.equalNodes(t, p)).count() > 1)
      .collect((Collectors.toList()));

    if (!identicalExpressions.isEmpty()) {
      diagnosticStorage.addDiagnostic(
        ctx,
        info.getMessage(onlyOperation.get(0).getText(), identicalExpressions.get(0).getText())
      );
    }

    return super.visitChildren(ctx);

  }

  private static boolean sufficientSize(BSLParser.ExpressionContext ctx) {
    return ctx.children.size() < MIN_EXPRESSION_SIZE;
  }

  private static boolean isUniformExpression(List<? extends BSLParser.OperationContext> operation) {
    List<Integer> groupOperation = groupIdenticalOperation(operation);

    return groupOperation.size() == 1
      && groupOperation.get(0) != BSLParser.MUL
      && groupOperation.get(0) != BSLParser.PLUS;
  }

  private static List<Integer> groupIdenticalOperation(List<? extends BSLParser.OperationContext> operation) {
    return operation
      .stream()
      .map((BSLParser.OperationContext o) -> o.start.getType())
      .distinct()
      .collect(Collectors.toList());
  }
}
