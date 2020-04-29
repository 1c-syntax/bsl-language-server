/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  scope = DiagnosticScope.BSL,
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_1,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.DEPRECATED,
    DiagnosticTag.ERROR
  }

)
public class UnsafeSafeModeMethodCallDiagnostic extends AbstractFindMethodDiagnostic {

  private static final Pattern SAFE_MODE_METHOD_NAME = Pattern.compile(
    "(БезопасныйРежим|SafeMode)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public UnsafeSafeModeMethodCallDiagnostic(DiagnosticInfo info) {
    super(info, SAFE_MODE_METHOD_NAME);
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (!SAFE_MODE_METHOD_NAME.matcher(ctx.methodName().getText()).matches()) {
      return false;
    }

    BSLParserRuleContext rootIfNode = Trees.getRootParent(ctx, BSLParser.RULE_ifStatement);
    BSLParserRuleContext rootExpressionNode = Trees.getRootParent(ctx, BSLParser.RULE_expression);
    BSLParserRuleContext currentRootMember = Trees.getRootParent(ctx, BSLParser.RULE_member);

    if (rootIfNode == null || rootExpressionNode == null || currentRootMember == null) {
      return false;
    }

    int indexOfCurrentMemberNode = rootExpressionNode.children.indexOf(currentRootMember);
    if (indexOfCurrentMemberNode != rootExpressionNode.getChildCount() - 1) {
      var nextNode = rootExpressionNode.children.get(indexOfCurrentMemberNode + 1);
      return !(Trees.nodeContains(nextNode, BSLParser.RULE_compareOperation));
    }

    return true;
  }
}
