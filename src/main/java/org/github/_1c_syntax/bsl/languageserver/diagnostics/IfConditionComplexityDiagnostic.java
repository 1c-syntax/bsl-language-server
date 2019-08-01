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
import org.antlr.v4.runtime.tree.Trees;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.Collection;
import java.util.Map;

@DiagnosticMetadata(
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 10
)

public class IfConditionComplexityDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MAX_IF_CONDITION_COMPLEXITY = 3;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_IF_CONDITION_COMPLEXITY,
    description = "Допустимое количество операндов в условии оператора Если"
  )

  private int maxIfConditionComplexity = MAX_IF_CONDITION_COMPLEXITY;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    maxIfConditionComplexity =
      (Integer) configuration.get("maxIfConditionComplexity");
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    Collection<ParseTree> boolOperations = Trees.findAllRuleNodes(ctx.expression(0), BSLParser.RULE_boolOperation);
    if (boolOperations.size() + 1 > maxIfConditionComplexity)
      diagnosticStorage.addDiagnostic(ctx.expression(0));
    return super.visitIfStatement(ctx);
  }

}

