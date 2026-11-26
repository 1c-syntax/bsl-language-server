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
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collection;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BRAINOVERLOAD
  }
)
public class NestedTernaryOperatorDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitIfBranch(BSLParser.IfBranchContext ctx) {
    BSLParser.ExpressionContext expressionContext = ctx.expression();
    findNestedTernaryOperator(expressionContext, 0);
    return super.visitIfBranch(ctx);
  }

  @Override
  public ParseTree visitElsifBranch(BSLParser.ElsifBranchContext ctx) {
    BSLParser.ExpressionContext expressionContext = ctx.expression();
    findNestedTernaryOperator(expressionContext, 0);
    return super.visitElsifBranch(ctx);
  }

  @Override
  public ParseTree visitTernaryOperator(BSLParser.TernaryOperatorContext ctx) {
    findNestedTernaryOperator(ctx, 1);
    return super.visitTernaryOperator(ctx);
  }

  private void findNestedTernaryOperator(ParserRuleContext ctx, int skip) {
    Collection<ParseTree> nestedTernaryOperators = Trees.findAllRuleNodes(ctx, BSLParser.RULE_ternaryOperator);
    if (nestedTernaryOperators.size() > skip) {
      nestedTernaryOperators.stream()
        .skip(skip)
        .forEach(parseTree -> diagnosticStorage.addDiagnostic((ParserRuleContext) parseTree));
    }
  }

}
