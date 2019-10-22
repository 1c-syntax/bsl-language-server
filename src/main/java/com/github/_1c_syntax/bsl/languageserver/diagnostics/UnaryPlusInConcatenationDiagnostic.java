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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 1
)
public class UnaryPlusInConcatenationDiagnostic extends AbstractVisitorDiagnostic {

  private static final Class<BSLParser.NumericContext> numericNode = BSLParser.NumericContext.class;

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {

    boolean expressionHasError = ctx.children.size() == 3 // у выражения должно быть три операнда
      && ctx.children.get(1).getChildCount() == 1 // второй из них - знак некоторой операции
      && ctx.children.get(1).getChild(0).toString().equals("+") // а именно "+"
      && ctx.children.get(2).getChildCount() == 2 // третий - выражение с унарной операцией, должно состоять из двух операндов
      && ctx.children.get(2).getChild(0).getChild(0).toString().equals("+") // первый из которых - "+"
      && !numericNode.isAssignableFrom(ctx.children.get(2).getChild(1).getChild(0).getClass()); // а второй не должен быть числом
    if (expressionHasError) {
      diagnosticStorage.addDiagnostic((TerminalNode) ctx.children.get(1).getChild(0));
    }
    return super.visitExpression(ctx);
  }

}
