/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MINOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }
)
public class StyleElementConstructorsDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern PATTERN = CaseInsensitivePattern.compile("^(Рамка|Цвет|Шрифт|Color|Border|Font)$");
  private static final Pattern QUOTE_PATTERN = Pattern.compile("\"");

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    var ctxTypeName = typeName(ctx);

    if (PATTERN.matcher(ctxTypeName).find()) {
      diagnosticStorage.addDiagnostic(ctx, info.getMessage(ctxTypeName));
    }

    return super.visitNewExpression(ctx);
  }

  private static String typeName(BSLParser.NewExpressionContext ctx) {
    if (ctx.typeName() != null) {
      return ctx.typeName().getText();
    }

    if (ctx.doCall().callParamList().isEmpty()) {
      return "";
    }

    return QUOTE_PATTERN.matcher(ctx.doCall().callParamList().callParam(0).getText()).replaceAll("");
  }

}
