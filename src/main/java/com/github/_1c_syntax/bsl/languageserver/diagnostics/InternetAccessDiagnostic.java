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
import com.github._1c_syntax.bsl.languageserver.utils.bsl.Constructors;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 60,
  tags = {
    DiagnosticTag.SUSPICIOUS
  },
  activatedByDefault = false
)

public class InternetAccessDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern PATTERN_NEW_EXPRESSION = CaseInsensitivePattern.compile(
    "FTPСоединение|FTPConnection|HTTPСоединение|HTTPConnection|WSОпределения|WSDefinitions|WSПрокси|WSProxy" +
      "|ИнтернетПочтовыйПрофиль|InternetMailProfile|ИнтернетПочта|InternetMail|Почта|Mail|HTTPЗапрос|HTTPRequest|" +
      "ИнтернетПрокси|InternetProxy");

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    Constructors.typeName(ctx).ifPresent((String typeName) -> {
      var matcherTypeName = PATTERN_NEW_EXPRESSION.matcher(typeName);
      if (matcherTypeName.matches()) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    });
    return super.visitNewExpression(ctx);
  }
}
