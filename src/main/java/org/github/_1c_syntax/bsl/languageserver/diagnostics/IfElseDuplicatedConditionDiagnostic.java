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
import org.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.List;

/**
 * @author Leon Chagelishvili <lChagelishvily@gmail.com>
 */
@DiagnosticMetadata
public class IfElseDuplicatedConditionDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    findDuplicatedExpression(ctx.expression());
    return super.visitIfStatement(ctx);
  }

  private void findDuplicatedExpression(List<BSLParser.ExpressionContext> expressionContexts) {
    for (int i = 0; i < expressionContexts.size(); i++) {
      checkExpression(expressionContexts, i);
    }
  }

  private void checkExpression(List<BSLParser.ExpressionContext> expressionContexts, int i) {
    BSLParser.ExpressionContext currentExpression = expressionContexts.get(i);
    for (int j = 0; j < expressionContexts.size(); j++) {
      if (!currentExpression.equals(expressionContexts.get(j))
        && DiagnosticHelper.equalNodes(currentExpression, expressionContexts.get(j))) {
        addDiagnostic(currentExpression);
        break;
      }
    }
  }

}

