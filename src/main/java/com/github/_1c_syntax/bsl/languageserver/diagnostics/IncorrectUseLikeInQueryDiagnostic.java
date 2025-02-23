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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.SQL,
    DiagnosticTag.UNPREDICTABLE
  },
  scope = DiagnosticScope.BSL
)
public class IncorrectUseLikeInQueryDiagnostic extends AbstractSDBLVisitorDiagnostic {

  @Override
  public ParseTree visitLikePredicate(SDBLParser.LikePredicateContext ctx) {
    checkRightStatement(ctx, ctx.LIKE(), ctx.expression());
    return super.visitLikePredicate(ctx);
  }

  private void checkRightStatement(BSLParserRuleContext ctx,
                                   @Nullable TerminalNode like,
                                   List<? extends SDBLParser.ExpressionContext> expressions) {

    if (like == null || expressions.size() <= 1) {
      return;
    }

    var right = expressions.get(1);
    var primitive = getPrimitiveExpression(right);
    if (primitive != null && (primitive.parameter() != null || primitive.multiString() != null)) {
      return;
    }

    diagnosticStorage.addDiagnostic(ctx);
  }

  @Nullable
  private static SDBLParser.PrimitiveExpressionContext getPrimitiveExpression(SDBLParser.ExpressionContext ctx) {
    var primitive = Trees.findAllRuleNodes(ctx, SDBLParser.RULE_primitiveExpression).stream()
      .filter(SDBLParser.PrimitiveExpressionContext.class::isInstance)
      .map(SDBLParser.PrimitiveExpressionContext.class::cast)
      .findFirst();
    return primitive.orElse(null);
  }
}
