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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import  java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }

)
public class ExportMethodPrivilegedModeDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern PRIVILEGED_MODE = CaseInsensitivePattern.compile(
  "УстановитьПривилегированныйРежим|SetPrivilegedMode"
  );

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    if (!PRIVILEGED_MODE.matcher(ctx.methodName().getText()).matches()
      || !isExplicitTrue(ctx)) {
      return super.visitGlobalMethodCall(ctx);
    }
    var parent = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_sub);

    if (parent instanceof BSLParser.SubContext methodContext) {
      documentContext.getSymbolTree()
      .getMethodSymbol(methodContext)
      .filter(method -> method.isExport())
      .ifPresent(method -> diagnosticStorage.addDiagnostic(
      ctx.methodName(),info.getMessage(method.getName())
      ));
    }
    return super.visitGlobalMethodCall(ctx);
  }
  private static boolean isExplicitTrue(BSLParser.GlobalMethodCallContext ctx) {
    if (ctx.doCall() == null || ctx.doCall().callParamList() == null) {
      return false;
    }
    var parameters = ctx.doCall().callParamList().callParam();

    if (parameters.size() != 1 || parameters.getFirst().expression() == null) {
      return false;
    }

    String value = parameters.getFirst().expression().getText();

    return value.equalsIgnoreCase("True")
      || value.equalsIgnoreCase("Истина");
  }

}
