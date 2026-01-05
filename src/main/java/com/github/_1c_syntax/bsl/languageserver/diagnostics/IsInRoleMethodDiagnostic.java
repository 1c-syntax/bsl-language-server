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
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
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
public class IsInRoleMethodDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern IS_IN_ROLE_NAME_PATTERN = CaseInsensitivePattern.compile(
    "(РольДоступна|IsInRole)"
  );

  private static final Pattern PRIVILEGED_MODE_NAME_PATTERN = CaseInsensitivePattern.compile(
    "(ПривилегированныйРежим|PrivilegedMode)"
  );
  private static final Set<Integer> ROOT_PARENTS_FOR_GLOBAL_METHODS =
    Set.of(BSLParser.RULE_ifStatement, BSLParser.RULE_assignment);

  private final Set<String> isInRoleVars = new HashSet<>();
  private final Set<String> privilegedModeNameVars = new HashSet<>();

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    isInRoleVars.clear();
    privilegedModeNameVars.clear();

    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitIfBranch(BSLParser.IfBranchContext ctx) {
    computeDiagnostics(ctx.expression());
    return super.visitIfBranch(ctx);
  }

  @Override
  public ParseTree visitElsifBranch(BSLParser.ElsifBranchContext ctx) {
    computeDiagnostics(ctx.expression());
    return super.visitElsifBranch(ctx);
  }

  private void computeDiagnostics(BSLParser.ExpressionContext expression) {
    Trees.findAllRuleNodes(expression, BSLParser.RULE_complexIdentifier).stream()
      .map(complexCtx -> (BSLParser.ComplexIdentifierContext) complexCtx)
      .filter(complexCtx -> isInRoleVars.contains(complexCtx.getText()))
      .filter(ctx -> checkStatement(ctx, expression))
      .forEach(diagnosticStorage::addDiagnostic);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    final var text = ctx.methodName().getText();
    if (IS_IN_ROLE_NAME_PATTERN.matcher(text).matches()) {
      handleIsInRoleGlobalMethod(ctx);
    } else if (PRIVILEGED_MODE_NAME_PATTERN.matcher(text).matches()) {
      handlePrivilegedModeGlobalMethod(ctx);
    }

    return super.visitGlobalMethodCall(ctx);
  }

  private void handleIsInRoleGlobalMethod(BSLParser.GlobalMethodCallContext ctx) {
    var rootParent = Trees.getRootParent(ctx, ROOT_PARENTS_FOR_GLOBAL_METHODS);
    if (rootParent == null) {
      return;
    }
    if (rootParent.getRuleIndex() == BSLParser.RULE_ifStatement) {
      if (checkStatement(ctx)) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    } else if (rootParent.getRuleIndex() == BSLParser.RULE_assignment) {
      addAssignedNameVar(rootParent, isInRoleVars);
    }
  }

  private void handlePrivilegedModeGlobalMethod(BSLParser.GlobalMethodCallContext ctx) {
    var assignmentNode = Trees.getRootParent(ctx, BSLParser.RULE_assignment);
    if (assignmentNode != null) {
      addAssignedNameVar(assignmentNode, privilegedModeNameVars);
    }
  }

  private static void addAssignedNameVar(ParserRuleContext assignmentNode, Set<String> nameVars) {
    var childNode = Trees.getFirstChild(assignmentNode, BSLParser.RULE_lValue);
    childNode.ifPresent(node -> nameVars.add(node.getText()));
  }

  @Override
  public ParseTree visitAssignment(BSLParser.AssignmentContext ctx) {
    var childNode = Trees.getFirstChild(ctx, BSLParser.RULE_lValue);
    childNode.ifPresent((ParserRuleContext node) ->
    {
      isInRoleVars.remove(node.getText());
      privilegedModeNameVars.remove(node.getText());
    });
    return super.visitAssignment(ctx);
  }

  private boolean checkStatement(ParserRuleContext ctx) {
    var parentExpression = Trees.getRootParent(ctx, BSLParser.RULE_expression);
    return checkStatement(ctx, parentExpression);
  }

  private boolean checkStatement(ParserRuleContext ctx, @Nullable ParserRuleContext parentExpression) {

    if (parentExpression == null) {
      return false;
    }

    var identifierList = Trees.findAllRuleNodes(parentExpression, BSLParser.RULE_complexIdentifier);
    for (ParseTree parseTree : identifierList) {
      if (privilegedModeNameVars.contains(parseTree.getText())) {
        return false;
      }
    }

    var nextGlobalMethodNode = Trees.getNextNode(parentExpression,
      ctx, BSLParser.RULE_globalMethodCall);

    boolean hasPrivilegedModeCheck = (nextGlobalMethodNode instanceof BSLParser.GlobalMethodCallContext gmcc
      && PRIVILEGED_MODE_NAME_PATTERN.matcher(gmcc
      .methodName().getText()).matches());

    return !hasPrivilegedModeCheck;
  }
}
