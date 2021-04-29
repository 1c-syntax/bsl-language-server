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
import org.jetbrains.annotations.Nullable;

import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
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
  public ParseTree visitSelectMember(SDBLParser.SelectMemberContext ctx) {
    checkRightStatement(ctx, ctx.LIKE(), ctx.selectStatement());
    return super.visitSelectMember(ctx);
  }

  @Override
  public ParseTree visitVirtualTableMember(SDBLParser.VirtualTableMemberContext ctx) {
    checkRightStatement(ctx, ctx.LIKE(), ctx.virtualTableStatement());
    return super.visitVirtualTableMember(ctx);
  }

  @Override
  public ParseTree visitJoinMember(SDBLParser.JoinMemberContext ctx) {
    checkRightStatement(ctx, ctx.LIKE(), ctx.joinStatement());
    return super.visitJoinMember(ctx);
  }

  @Override
  public ParseTree visitWhereMember(SDBLParser.WhereMemberContext ctx) {
    checkRightStatement(ctx, ctx.LIKE(), ctx.whereStatement());
    return super.visitWhereMember(ctx);
  }

  @Override
  public ParseTree visitGroupByMember(SDBLParser.GroupByMemberContext ctx) {
    checkRightStatement(ctx, ctx.LIKE(), ctx.groupByStatement());
    return super.visitGroupByMember(ctx);
  }

  private void checkRightStatement(BSLParserRuleContext ctx,
                                   @Nullable TerminalNode like,
                                   List<? extends BSLParserRuleContext> statements) {

    if (like == null || statements.size() <= 1) {
      return;
    }

    var right = statements.get(1);
    var statement = (SDBLParser.StatementContext) Trees.getNextNode(right, right, SDBLParser.RULE_statement);
    if (statement.parameter().isEmpty()
      && statement.multiString() == null) {
      diagnosticStorage.addDiagnostic(ctx);
    }

  }
}
