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
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5
)
public class IdenticalExpressionsDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MIN_EXPRESSION_SIZE = 3;

  private static boolean sufficientSize(BSLParser.ExpressionContext ctx) {
    return ctx.children.size() < MIN_EXPRESSION_SIZE;
  }

  private static boolean isUniformExpression(List<ParseTree> onlyOperation) {

    List<ParseTree> groupOperation = onlyOperation
      .stream()
      .reduce(
        new ArrayList<>(),
        (List<ParseTree> acc, ParseTree el) -> {
          if(acc.stream().noneMatch((ParseTree node) -> DiagnosticHelper.equalNodes(node, el))) {
            acc.add(el);
          }

          return acc;
        },
        (List<ParseTree> acc, List<ParseTree> acc2) -> {
          acc.addAll(acc2);
          return acc;
        });

    return groupOperation.size() == 1
      && ((BSLParser.OperationContext) groupOperation.get(0)).start.getType() != BSLParser.MUL;
  }

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {

    List<ParseTree> onlyOperation = ctx.children
      .stream()
      .filter((ParseTree node) -> (node instanceof BSLParser.OperationContext))
      .collect(Collectors.toList());

    if(sufficientSize(ctx) || !isUniformExpression(onlyOperation)) {
      return super.visitChildren(ctx);
    }

    List<ParseTree> onlyMembers = ctx
      .children
      .stream()
      .filter((ParseTree node) -> !(node instanceof BSLParser.OperationContext))
      .collect((Collectors.toList()));

    List<ParseTree> identicalExpressions = onlyMembers
      .stream()
      .filter((ParseTree t) -> onlyMembers
        .stream()
        .filter((ParseTree p) -> DiagnosticHelper.equalNodes(t, p)).count() > 1)
      .collect((Collectors.toList()));

    if(!identicalExpressions.isEmpty()) {
      diagnosticStorage.addDiagnostic(
        ctx,
        getDiagnosticMessage(onlyOperation.get(0).getText(), identicalExpressions.get(0).getText())
      );
    }

    return super.visitChildren(ctx);

  }

}
