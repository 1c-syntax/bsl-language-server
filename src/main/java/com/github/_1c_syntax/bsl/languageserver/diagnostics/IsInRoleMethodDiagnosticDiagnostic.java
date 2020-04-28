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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR
  }

)
public class IsInRoleMethodDiagnosticDiagnostic extends AbstractVisitorDiagnostic {

  private static final HashSet<String> IS_IN_ROLE_VARS = new HashSet<>();
  private static final HashSet<String> PRIVILEGED_MODE_NAME_VARS = new HashSet<>();

  private static final Pattern IS_IN_ROLE_NAME_PATTERN = Pattern.compile(
    "(РольДоступна|IsInRole)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern PRIVILEGED_MODE_NAME_PATTERN = Pattern.compile(
    "(PrivilegedMode|ПривилегированныйРежим)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public IsInRoleMethodDiagnosticDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitIfBranch(BSLParser.IfBranchContext ctx) {
    Collection<ParseTree> listIdentifier = Trees.findAllRuleNodes(ctx, BSLParser.RULE_complexIdentifier);

    if (listIdentifier.isEmpty()) {
      return super.visitIfBranch(ctx);
    }

    listIdentifier.stream().map(complexCtx -> (BSLParser.ComplexIdentifierContext)complexCtx)
      .filter(complexCtx -> IS_IN_ROLE_VARS.contains(complexCtx.getText()))
      .filter(IsInRoleMethodDiagnosticDiagnostic::checkStatement)
      .forEach(diagnosticStorage::addDiagnostic);

    return super.visitIfBranch(ctx);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (IS_IN_ROLE_NAME_PATTERN.matcher(ctx.methodName().getText()).matches()) {
      handleIsInRoleGlobalMethod(ctx);
    } else if (PRIVILEGED_MODE_NAME_PATTERN.matcher(ctx.methodName().getText()).matches()) {
      handlePrivilegedModeGlobalMethod(ctx);
    } else {
      return super.visitGlobalMethodCall(ctx);
    }

    return super.visitGlobalMethodCall(ctx);
  }

  private void handleIsInRoleGlobalMethod(BSLParser.GlobalMethodCallContext ctx) {
    var ifStatementNode = Trees.getRootParent(ctx, BSLParser.RULE_ifStatement);
    if (ifStatementNode != null && checkStatement(ctx)) {
      diagnosticStorage.addDiagnostic(ctx);
    }

    var assignmentNode = Trees.getRootParent(ctx, BSLParser.RULE_assignment);
    if (assignmentNode != null) {
      var childNodes = Trees.getChildren(assignmentNode, BSLParser.RULE_lValue);
      if (!childNodes.isEmpty()) {
        IS_IN_ROLE_VARS.add(childNodes.get(0).getText());
      }
    }
  }

  private static void handlePrivilegedModeGlobalMethod(BSLParser.GlobalMethodCallContext ctx) {
    var assignmentNode = Trees.getRootParent(ctx, BSLParser.RULE_assignment);
    if (assignmentNode != null) {
      var childNodes = Trees.getChildren(assignmentNode, BSLParser.RULE_lValue);
      if (!childNodes.isEmpty()) {
        PRIVILEGED_MODE_NAME_VARS.add(childNodes.get(0).getText());
      }
    }
  }

  @Override
  public ParseTree visitAssignment(BSLParser.AssignmentContext ctx) {
    var childNodes = Trees.getChildren(ctx, BSLParser.RULE_lValue);

    if (!childNodes.isEmpty()) {
      IS_IN_ROLE_VARS.remove(childNodes.get(0).getText());
      PRIVILEGED_MODE_NAME_VARS.remove(childNodes.get(0).getText());
    }
    return super.visitAssignment(ctx);
  }

  private static boolean checkStatement(BSLParserRuleContext ctx) {
    var parentExpression = Trees.getRootParent(ctx, BSLParser.RULE_expression);

    if (parentExpression == null) {
      return false;
    }

    boolean hasPrivilegedModeCheck = false;

    var identifierList = Trees.findAllRuleNodes(parentExpression, BSLParser.RULE_complexIdentifier);
    for (ParseTree parseTree : identifierList) {
      if (PRIVILEGED_MODE_NAME_VARS.contains(parseTree.getText())) {
        hasPrivilegedModeCheck = true;
      }
    }

    if (!hasPrivilegedModeCheck) {
      var nextGlobalMethodNode = Trees.getNextNode(parentExpression,
        ctx, BSLParser.RULE_globalMethodCall);

      hasPrivilegedModeCheck = (nextGlobalMethodNode instanceof BSLParser.GlobalMethodCallContext
        && PRIVILEGED_MODE_NAME_PATTERN.matcher(((BSLParser.GlobalMethodCallContext) nextGlobalMethodNode)
        .methodName().getText()).matches());
    }

    return !hasPrivilegedModeCheck;
  }
}
