/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.CallStatementContext;
import com.github._1c_syntax.bsl.parser.BSLParser.ComplexIdentifierContext;
import com.github._1c_syntax.bsl.parser.BSLParser.LValueContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.function.Predicate;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.CLUMSY
  }
)
public class UseLessForEachDiagnostic extends AbstractVisitorDiagnostic {

  private static Predicate<ParseTree> parentClassMatchTo(Class<?> clazzName) {
    return e -> e.getParent().getClass().equals(clazzName);
  }

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {

    TerminalNode iterator = ctx.IDENTIFIER();
    String iteratorIdName = iterator.getText();

    boolean isVariable = documentContext.getSymbolTree().getVariables()
      .stream()
      .filter(variableSymbol -> variableSymbol.getKind() == VariableKind.GLOBAL
        || variableSymbol.getKind() == VariableKind.MODULE)
      .anyMatch(variableSymbol -> variableSymbol.getName().equalsIgnoreCase(iteratorIdName));

    if (isVariable) {
      return super.visitForEachStatement(ctx);
    }

    boolean hasUsage = Trees.findAllTokenNodes(ctx.codeBlock(), BSLParser.IDENTIFIER)
      .stream()
      .filter(node -> iteratorIdName.equalsIgnoreCase(node.getText()))
      .anyMatch(parentClassMatchTo(ComplexIdentifierContext.class)
        .or(parentClassMatchTo(LValueContext.class))
        .or(parentClassMatchTo(CallStatementContext.class)));

    if (!hasUsage) {
      diagnosticStorage.addDiagnostic(iterator.getSymbol());
    }

    return super.visitForEachStatement(ctx);
  }

}


