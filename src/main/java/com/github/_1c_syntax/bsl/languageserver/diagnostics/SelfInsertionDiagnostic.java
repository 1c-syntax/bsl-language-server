/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.UNPREDICTABLE,
    DiagnosticTag.PERFORMANCE
  }
)
public class SelfInsertionDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern deletePattern = CaseInsensitivePattern.compile(
    "(вставить|добавить|insert|add)"
  );

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {

    if (ctx.globalMethodCall() != null) {
      return ctx;
    }

    String identifier = ctx.IDENTIFIER().getText().trim();
    BSLParser.MethodCallContext methodCall = ctx.accessCall().methodCall();

    if (deletePattern.matcher(methodCall.methodName().getText()).matches()) {
      List<? extends BSLParser.CallParamContext> callParams = methodCall
        .doCall()
        .callParamList()
        .callParam();

      for (BSLParser.CallParamContext param : callParams) {
        if (param.getText().trim().equals(identifier)) {
          diagnosticStorage.addDiagnostic(ctx);
        }
      }
    }

    return ctx;
  }

}
