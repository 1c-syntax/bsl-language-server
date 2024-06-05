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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.Set;
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

  private static final Pattern SAFE_MODE_METHOD_NAME = CaseInsensitivePattern.compile(
    "(БезопасныйРежим|SafeMode)"
  );
  private static final Set<Integer> ROOT_LIST = Set.of(
    BSLParser.RULE_ifBranch, BSLParser.RULE_elsifBranch, BSLParser.RULE_expression,
    BSLParser.RULE_codeBlock, BSLParser.RULE_assignment);
  private static final Set<Integer> IF_BRANCHES = Set.of(
    BSLParser.RULE_ifBranch, BSLParser.RULE_elsifBranch);

  public UnsafeSafeModeMethodCallDiagnostic() {
    super(SAFE_MODE_METHOD_NAME);
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (!super.checkGlobalMethodCall(ctx)) {
      return false;
    }

    BSLParser.MemberContext currentRootMember =
      (BSLParser.MemberContext) Trees.getRootParent(ctx, BSLParser.RULE_member);
    if (currentRootMember == null) {
      return false;
    }
    return nonValidExpression(currentRootMember);
  }

  private static boolean nonValidExpression(BSLParser.MemberContext currentRootMember) {
    if (currentRootMember.unaryModifier() != null) {
      return true;
    }

    BSLParserRuleContext rootExpressionNode = currentRootMember.getParent();

    BSLParserRuleContext rootIfNode = Trees.getRootParent(rootExpressionNode, ROOT_LIST);
    if (rootIfNode == null || rootIfNode.getRuleIndex() == BSLParser.RULE_codeBlock) {
      return false;
    }
    if (rootExpressionNode.getChildCount() == 1 && IF_BRANCHES.contains(rootIfNode.getRuleIndex())) {
      return true;
    }

    return haveNeighboorBooleanOperator(currentRootMember, rootExpressionNode);
  }

  private static boolean haveNeighboorBooleanOperator(BSLParserRuleContext currentRootMember,
                                                      BSLParserRuleContext rootExpressionNode) {
    var haveNeighbourBoolOperation = false;
    int indexOfCurrentMemberNode = rootExpressionNode.children.indexOf(currentRootMember);
    if (indexOfCurrentMemberNode > 0) {
      var prev = (BSLParserRuleContext) rootExpressionNode.children.get(indexOfCurrentMemberNode - 1);
      if (Trees.nodeContains(prev, BSLParser.RULE_compareOperation)) {
        return false;
      }
      haveNeighbourBoolOperation = Trees.nodeContains(prev, BSLParser.RULE_boolOperation);
    }
    if (indexOfCurrentMemberNode < rootExpressionNode.getChildCount() - 1) {

      var next = (BSLParserRuleContext) rootExpressionNode.children.get(indexOfCurrentMemberNode + 1);
      if (Trees.nodeContains(next, BSLParser.RULE_compareOperation)) {
        return false;
      }
      if (!haveNeighbourBoolOperation) {
        haveNeighbourBoolOperation = Trees.nodeContains(next, BSLParser.RULE_boolOperation);
      }
    }
    return haveNeighbourBoolOperation;
  }
}
