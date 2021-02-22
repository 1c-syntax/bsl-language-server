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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.UNPREDICTABLE
  },
  scope = DiagnosticScope.BSL

)
public class LogicalOrInTheWhereSectionOfQueryDiagnostic extends AbstractSDBLVisitorDiagnostic {

  private static final Set<Integer> ROOT_LIST = Set.of(
    SDBLParser.RULE_where, SDBLParser.RULE_query, SDBLParser.RULE_temparyTableMainQuery,
    SDBLParser.RULE_temparyTableQuery);

  @Override
  public ParseTree visitBoolOperation(SDBLParser.BoolOperationContext ctx) {

    TerminalNode orNode = ctx.OR();
    if (orNode != null) {
      BSLParserRuleContext whereCtx = Trees.getRootParent(ctx, ROOT_LIST);
      if (whereCtx != null && whereCtx.getRuleIndex() == SDBLParser.RULE_where){
        diagnosticStorage.addDiagnostic(orNode);
      }
    }
    return super.visitBoolOperation(ctx);
  }
}
