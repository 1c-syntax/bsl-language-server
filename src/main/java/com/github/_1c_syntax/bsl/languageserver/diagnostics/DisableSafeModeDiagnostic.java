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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.Optional;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.SUSPICIOUS
  },
  scope = DiagnosticScope.BSL
)
public class DisableSafeModeDiagnostic extends AbstractFindMethodDiagnostic {
  private static final Pattern methodPattern = CaseInsensitivePattern.compile(
    "УстановитьБезопасныйРежим|SetSafeMode|УстановитьОтключениеБезопасногоРежима|SetSafeModeDisabled");
  private static final Pattern safeModePattern = CaseInsensitivePattern.compile(
    "УстановитьБезопасныйРежим|SetSafeMode");

  public DisableSafeModeDiagnostic() {
    super(methodPattern);
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    final var result = super.checkGlobalMethodCall(ctx);
    if (!result) {
      return false;
    }
    final int enabledValue;
    if(safeModePattern.matcher(ctx.methodName().getText()).matches()){
      enabledValue = BSLParser.TRUE;
    } else {
      enabledValue = BSLParser.FALSE;
    }
    return !enabledCall(ctx, enabledValue);
  }

  private static boolean enabledCall(BSLParser.GlobalMethodCallContext ctx, int enabledValue) {
    return Optional.of(ctx)
      .map(BSLParser.GlobalMethodCallContext::doCall)
      .map(BSLParser.DoCallContext::callParamList)
      .map(BSLParser.CallParamListContext::callParam)
      .filter(callParamContexts -> callParamContexts.size() == 1)
      .map(callParamContexts -> callParamContexts.get(0))
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .map(memberContexts -> memberContexts.get(0))
      .map(BSLParser.MemberContext::constValue)
      .filter(constValueContext -> constValueContext.getToken(enabledValue, 0) != null)
      .isPresent();
  }
}
