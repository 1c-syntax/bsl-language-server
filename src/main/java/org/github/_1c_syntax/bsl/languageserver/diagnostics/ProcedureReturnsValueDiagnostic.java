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
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLLexer;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.Collection;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5
)
public class ProcedureReturnsValueDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {

    Collection<ParseTree> statements = Trees.findAllRuleNodes(ctx, BSLParser.RULE_statement);
    statements.forEach(thisStatement -> localVisitStatement((BSLParser.StatementContext) thisStatement));
    return ctx;

  }

  public ParseTree localVisitStatement(BSLParser.StatementContext ctx) {

    if (ctx.preprocessor()!= null || (ctx.getChildCount() == 1 && ctx.SEMICOLON() != null)){
      return super.visitStatement(ctx);
    }

    if (ctx.getStart().getType() == BSLLexer.RETURN_KEYWORD && ctx.getTokens().size() > 1)
    {
      addDiagnostic(ctx);
    }

    return super.visitStatement(ctx);

  }

}
