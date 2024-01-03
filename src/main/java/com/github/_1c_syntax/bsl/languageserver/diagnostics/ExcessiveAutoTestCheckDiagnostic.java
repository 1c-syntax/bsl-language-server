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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  modules = {
    ModuleType.FormModule,
    ModuleType.ObjectModule,
    ModuleType.RecordSetModule,
    ModuleType.CommonModule
  },
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.DEPRECATED
  }
)
public class ExcessiveAutoTestCheckDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern ERROR_EXPRESSION = CaseInsensitivePattern.compile(
    "(\\.Свойство\\(\"АвтоТест\"\\)|=\"АвтоТест\"|\\.Property\\(\"AutoTest\"\\)|=\"AutoTest\")$"
  );

  @Override
  public ParseTree visitIfBranch(BSLParser.IfBranchContext ctx) {

    if (expressionMatchesPattern(ctx.expression()) && codeBlockWithOnlyReturn(ctx.codeBlock())) {
      diagnosticStorage.addDiagnostic(ctx.getParent());
      return ctx;
    }

    return super.visitIfBranch(ctx);
  }

  private static boolean expressionMatchesPattern(BSLParser.ExpressionContext expression) {
    return ERROR_EXPRESSION.matcher(expression.getText()).find();
  }

  private static boolean codeBlockWithOnlyReturn(BSLParser.CodeBlockContext codeBlock) {
    List<? extends BSLParser.StatementContext> statements = codeBlock.statement();

    if (statements.size() == 1) {
      BSLParser.CompoundStatementContext compoundStatement = statements.get(0).compoundStatement();

      if (compoundStatement != null) {
        return compoundStatement.returnStatement() != null;
      }
    }

    return false;
  }
}
