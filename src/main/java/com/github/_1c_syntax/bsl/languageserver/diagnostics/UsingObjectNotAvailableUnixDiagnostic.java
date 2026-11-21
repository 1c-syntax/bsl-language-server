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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  minutesToFix = 30,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.LOCKINOS
  }
)
public class UsingObjectNotAvailableUnixDiagnostic extends AbstractVisitorDiagnostic {

  private final TypeService typeService;

  public UsingObjectNotAvailableUnixDiagnostic(TypeService typeService) {
    this.typeService = typeService;
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    var name = ctx.methodName().getStart().getText();
    if (isGetCOMObject(name) && documentContext.getSymbolTree().getMethodSymbol(name).isEmpty()
      && typeService.accessTargetsAt(documentContext, ctx.methodName().IDENTIFIER(), null).stream()
        .allMatch(member -> member.descriptor().standardLibrary())) {
      if (!hasEnclosingPlatformCondition(ctx)) {
        diagnosticStorage.addDiagnostic(ctx, info.getMessage(name));
      }
    }
    return super.visitGlobalMethodCall(ctx);
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    var name = ctx.methodName().getStart().getText();
    if (isGetCOMObject(name)) {
      boolean htmlShellCall = typeService.accessTargetsAt(documentContext, ctx.methodName().IDENTIFIER(), null).stream()
        .anyMatch(member -> member.descriptor().standardLibrary() && member.owner() != null
          && ("ОболочкаHTMLДокумента".equalsIgnoreCase(member.owner().qualifiedName())
          || "HTMLDocumentShell".equalsIgnoreCase(member.owner().qualifiedName())));
      if (htmlShellCall && !hasEnclosingPlatformCondition(ctx)) {
        diagnosticStorage.addDiagnostic(ctx, info.getMessage(name));
      }
    }
    return super.visitMethodCall(ctx);
  }

  private static boolean isGetCOMObject(String name) {
    return "ПолучитьCOMОбъект".equalsIgnoreCase(name) || "GetCOMObject".equalsIgnoreCase(name);
  }

  private static final Pattern patternNewExpression = CaseInsensitivePattern.compile(
    "^(COMОбъект|COMObject|Почта|Mail)"
  );

  private static final Pattern patternTypePlatform = CaseInsensitivePattern.compile(
    "Linux_x86|Windows|MacOS"
  );

  /**
   * Проверяем создание COMОбъект или Почта, если окружающие условия не проверяют тип платформы.
   * Пример:
   * Компонента = Новый COMОбъект("System.Text.UTF8Encoding");
   */
  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    var typeNameContext = ctx.typeName();
    if (typeNameContext == null) {
      return super.visitNewExpression(ctx);
    }
    var matcherTypeName = patternNewExpression.matcher(typeNameContext.getText());
    if (matcherTypeName.find() && !hasEnclosingPlatformCondition(ctx)) {
      diagnosticStorage.addDiagnostic(ctx, info.getMessage(typeNameContext.getText()));
    }
    return super.visitNewExpression(ctx);
  }

  private static boolean hasEnclosingPlatformCondition(ParserRuleContext element) {
    for (var ancestor = element.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
      var condition = switch (ancestor) {
        case BSLParser.IfBranchContext branch -> branch.expression();
        case BSLParser.ElsifBranchContext branch -> branch.expression();
        default -> null;
      };
      if (condition != null && patternTypePlatform.matcher(condition.getText()).find()) {
        return true;
      }
    }
    return false;
  }

}
