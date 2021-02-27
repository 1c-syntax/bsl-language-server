/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }

)
public class UsageWriteLogEventDiagnostic extends AbstractFindMethodDiagnostic {

  private static final Pattern WRITELOGEVENT = CaseInsensitivePattern.compile(
    "записьжурналарегистрации|writelogevent"
  );
  //  private static final Pattern DETAIL_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
  private static final Pattern PATTERN_DETAIL_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
    "подробноепредставлениеошибки|detailerrordescription"
  );
  private static final Pattern PATTERN_ERROR_INFO = CaseInsensitivePattern.compile(
    "информацияобошибке|errorinfo"
  );
  private static final Pattern PATTERN_BRIEF_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
    "описаниеошибки|errordescription"
  );
  private static final Pattern PATTERN_EVENT_LOG_LEVEL = CaseInsensitivePattern.compile(
    "уровеньжурналарегистрации|eventloglevel"
  );
  private static final Pattern PATTERN_ERROR = CaseInsensitivePattern.compile(
    "ошибка|error"
  );
  private static final Set<Integer> ROOT_PARENTS_FOR_EVENT_LOG =
    Set.of(BSLParser.RULE_exceptCodeBlock, BSLParser.RULE_subCodeBlock, BSLParser.RULE_fileCodeBlock, BSLParser.RULE_fileCodeBlockBeforeSub);

  public UsageWriteLogEventDiagnostic() {
    super(WRITELOGEVENT);
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    check(ctx);
    return false;
  }

  private void check(BSLParser.GlobalMethodCallContext ctx) {

    if (!super.checkGlobalMethodCall(ctx)) {
      return;
    }

    final var callParamListContext = ctx.doCall().callParamList();
    final var callParamContexts = callParamListContext.callParam();
    if (callParamContexts.size() < 5) {
      fireIssue(ctx, "wrongNumberMessage");
      return;
    }

    final BSLParser.CallParamContext secondParamCtx = callParamContexts.get(1);
    if (secondParamCtx.getChildCount() == 0) {
      fireIssue(ctx, "noSecondParameter");
      return;
    }

    final BSLParser.CallParamContext commentsCtx = callParamContexts.get(4);
    if (commentsCtx.getChildCount() == 0) {
      fireIssue(ctx, "noComment");
      return;
    }

    if (!haveErrorLogLevel(secondParamCtx) && insideExceptBlock(ctx)){
      fireIssue(ctx, "noErrorLogLevelInsideExceptBlock");
      return;
    }

    if (!isRightComments(commentsCtx)) {
      fireIssue(ctx, "noDetailErrorDescription");
    }

  }

  private void fireIssue(BSLParser.GlobalMethodCallContext ctx, String wrongNumberMessage2) {
    String wrongNumberMessage = info.getResourceString(
      wrongNumberMessage2);
    diagnosticStorage.addDiagnostic(ctx, wrongNumberMessage);
  }

  private static boolean haveErrorLogLevel(BSLParser.CallParamContext callParamContext) {
    final var complexIdentifier = Optional.of(callParamContext.expression())
      .map(BSLParser.ExpressionContext::member)
      .filter(memberContexts -> memberContexts.size() == 1)
      .map(memberContexts -> memberContexts.get(0))
      .map(BSLParser.MemberContext::complexIdentifier)
      .filter(identifier -> identifier.IDENTIFIER() != null);
    if (complexIdentifier.isEmpty()){
      return true;
    }

    final var result = complexIdentifier
      .filter(identifier -> PATTERN_EVENT_LOG_LEVEL.matcher(identifier.IDENTIFIER().getText()).matches())
      .filter(identifier -> identifier.modifier().size() == 1)
      .map(identifier -> identifier.modifier(0))
      .map(BSLParser.ModifierContext::accessProperty)
      .filter(accessProperty -> PATTERN_ERROR.matcher(accessProperty.IDENTIFIER().getText()).matches())
      .isPresent();
    if (result){
      return true;
    }
    return complexIdentifier
      .filter(identifier -> identifier.getChildCount() == 1)
      .isPresent();
  }

  private boolean isRightComments(BSLParser.CallParamContext commentsCtx) {

    final var codeBlockContext = (BSLParser.CodeBlockContext) Trees.getRootParent(commentsCtx, BSLParser.RULE_codeBlock);
    if (haveRaiseStatement(codeBlockContext)) {
      return true;
    }
    return isValidExpression(codeBlockContext, commentsCtx.expression(), true);
  }

  private boolean haveRaiseStatement(BSLParser.CodeBlockContext codeBlockContext) {
    return codeBlockContext
      .statement().stream()
      .map(BSLParser.StatementContext::compoundStatement)
      .filter(Objects::nonNull)
      .map(BSLParser.CompoundStatementContext::raiseStatement)
      .anyMatch(Objects::nonNull);
  }

  // если в одном блоке с ЗаписьЖР есть присвоение переменой из выражения-комментария,
  // то проверим, что в присвоении есть ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()
  // если есть какая-то переменная, определенная на уровень выше (например, параметр метода), то не анализируем ее

  private static boolean isValidExpression(BSLParser.CodeBlockContext codeBlock, @Nullable BSLParser.ExpressionContext expression, boolean checkPrevAssignment) {
    if (expression == null){
      return true;
    }
    final var assignmentGlobalCalls = Trees.findAllRuleNodes(expression, BSLParser.RULE_globalMethodCall);
    if (!assignmentGlobalCalls.isEmpty()) {
      if (isRightErrorDescriptionCall(assignmentGlobalCalls)) {
        return true;
      }
      if (isHaveBriefErrorDescription(assignmentGlobalCalls)) {
        return false;
      }
    }
    return isValidExpression(expression, codeBlock, checkPrevAssignment);
  }

  private static boolean isRightErrorDescriptionCall(Collection<ParseTree> globalCalls) {
    return globalCalls.stream()
      .filter(ctx -> ctx instanceof BSLParser.GlobalMethodCallContext)
      .filter(ctx -> isAppropriateName((BSLParser.GlobalMethodCallContext) ctx, PATTERN_DETAIL_ERROR_DESCRIPTION))
      .anyMatch(ctx -> haveFirstDescendantGlobalCall((BSLParser.GlobalMethodCallContext) ctx));
  }

  private static boolean isAppropriateName(BSLParser.GlobalMethodCallContext ctx, Pattern patternDetailErrorDescription) {
    return patternDetailErrorDescription.matcher(ctx.methodName()
      .getText()).matches();
  }

  private static boolean haveFirstDescendantGlobalCall(BSLParser.GlobalMethodCallContext globalCallCtx) {
    final var errorInfoCall = Trees.findAllRuleNodes(globalCallCtx, BSLParser.RULE_globalMethodCall).stream()
      .filter(ctx -> isAppropriateName((BSLParser.GlobalMethodCallContext) ctx, UsageWriteLogEventDiagnostic.PATTERN_ERROR_INFO))
      .findFirst();
    return errorInfoCall.isPresent();
  }

  private static boolean isHaveBriefErrorDescription(Collection<ParseTree> globalCalls) {
    return globalCalls.stream()
      .filter(ctx -> ctx instanceof BSLParser.GlobalMethodCallContext)
      .anyMatch(ctx -> isAppropriateName((BSLParser.GlobalMethodCallContext) ctx, PATTERN_BRIEF_ERROR_DESCRIPTION));
  }

  private static boolean isValidExpression(BSLParser.ExpressionContext ctx, BSLParser.CodeBlockContext codeBlock,
                                           boolean checkPrevAssignment) {
    return ctx.member().stream()
      .allMatch(memberContext -> isValidExpression(memberContext, codeBlock, checkPrevAssignment));
  }

  private static boolean isValidExpression(BSLParser.MemberContext ctx, BSLParser.CodeBlockContext codeBlock,
                                           boolean checkPrevAssignment) {
    if (ctx.constValue() != null) {
      return false;
    }
    if (checkPrevAssignment){
      final var var = ctx.complexIdentifier();
      if (var != null) {
        return isValidVarAssigment(var, codeBlock);
      }
    }
    return false;
  }

  private static boolean isValidVarAssigment(BSLParser.ComplexIdentifierContext var, BSLParser.CodeBlockContext codeBlock) {
      String varName = var.getText();
      final var assignment = getAssignment(varName, codeBlock);
      if (assignment.isEmpty()) {
        return true;
      }
    return isValidExpression(codeBlock, assignment.get().expression(), false);
  }

  @NotNull
  private static Optional<BSLParser.AssignmentContext> getAssignment(String varName, BSLParser.CodeBlockContext codeBlock) {
    return Optional.of(codeBlock)
      .map(BSLParser.CodeBlockContext::statement)
      .filter(statementContexts -> !statementContexts.isEmpty())
      .map(statementContexts -> codeBlock.statement(0))
      .map(BSLParser.StatementContext::assignment)
      .filter(Objects::nonNull)
      .filter(assignmentContext -> assignmentContext.lValue().getText().equalsIgnoreCase(varName));
  }

  private boolean insideExceptBlock(BSLParser.GlobalMethodCallContext ctx) {
    final var rootParent = Trees.getRootParent(ctx, ROOT_PARENTS_FOR_EVENT_LOG);
    return rootParent != null && rootParent.getRuleIndex() == BSLParser.RULE_exceptCodeBlock;
  }

}
