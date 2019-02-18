/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>
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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Trees;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.bsl.parser.BSLParser;

public class SelfAssignDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Warning;
  }

  @Override
  public ParseTree visitAssignment(BSLParser.AssignmentContext ctx) {

    BSLParser.ExpressionContext expression = ctx.expression();

    if (expression == null) {
      return super.visitAssignment(ctx);
    }

    if (ctx.complexIdentifier().getText().equalsIgnoreCase(expression.getText())
      && getDescendantsCount(ctx.complexIdentifier()) == getDescendantsCount(expression)) {
      addDiagnostic(ctx);
    }

    return super.visitAssignment(ctx);
  }

  private static int getDescendantsCount(ParserRuleContext tree) {

    return ((int) Trees.getDescendants(tree).stream()
      .filter(node -> (node instanceof TerminalNode)).count());

  }

}
