/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BRAINOVERLOAD
  }
)

public class IfConditionComplexityDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MAX_IF_CONDITION_COMPLEXITY = 3;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_IF_CONDITION_COMPLEXITY
  )
  private int maxIfConditionComplexity = MAX_IF_CONDITION_COMPLEXITY;

  @Override
  public ParseTree visitIfBranch(BSLParser.IfBranchContext ctx) {
    checkExpressionAndRaise(ctx.expression());
    return super.visitIfBranch(ctx);
  }

  @Override
  public ParseTree visitElsifBranch(BSLParser.ElsifBranchContext ctx) {
    checkExpressionAndRaise(ctx.expression());
    return super.visitElsifBranch(ctx);
  }

  private void checkExpressionAndRaise(BSLParser.ExpressionContext expression) {
    if (Trees.findAllRuleNodes(expression, BSLParser.RULE_boolOperation).size() + 1 > maxIfConditionComplexity) {
      diagnosticStorage.addDiagnostic(expression);
    }
  }
}

