/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.SECURITY_HOTSPOT,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.DESIGN
  }
)
public class UsingExternalCodeToolsDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern externalCodeToolsName = CaseInsensitivePattern.compile(
    "^(ВнешниеОбработки|ExternalDataProcessors|ВнешниеОтчеты|ExternalReports|" +
      "РасширенияКонфигурации|ConfigurationExtensions)"
  );

  private static final Pattern externalCodeToolsMethodsName = CaseInsensitivePattern.compile(
    "^(Создать|Create|Подключить|Connect)"
  );

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {
    if (ctx.globalMethodCall() == null) {
      checkUseExternalCodeTools(ctx, ctx.IDENTIFIER());
    }
    return super.visitCallStatement(ctx);
  }

  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    checkUseExternalCodeTools(ctx, ctx.IDENTIFIER());
    return super.visitComplexIdentifier(ctx);
  }

  private void checkUseExternalCodeTools(BSLParserRuleContext ctx, TerminalNode identifier) {
    if (identifier != null
      && externalCodeToolsName.matcher(identifier.getText()).matches()
      && Trees.findAllRuleNodes(ctx, BSLParser.RULE_methodCall)
      .stream()
      .anyMatch(child ->
        externalCodeToolsMethodsName.matcher(((BSLParser.MethodCallContext) child).getStart().getText()).matches())) {
      diagnosticStorage.addDiagnostic(ctx);
    }
  }
}
