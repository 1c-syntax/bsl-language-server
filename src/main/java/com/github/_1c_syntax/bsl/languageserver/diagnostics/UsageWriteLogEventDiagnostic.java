/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
public class UsageWriteLogEventDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern WRITELOGEVENT = CaseInsensitivePattern.compile(
    "записьжурналарегистрации|writelogevent"
  );
  private static final Pattern PATTERN_DETAIL_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
    "подробноепредставлениеошибки|detailerrordescription"
  );
  private static final Pattern PATTERN_BRIEF_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
    "краткоепредставлениеошибки|brieferrordescription"
  );
  private static final Pattern PATTERN_ERROR_INFO = CaseInsensitivePattern.compile(
    "информацияобошибке|errorinfo"
  );
  private static final Pattern PATTERN_SIMPLE_ERROR_DESCRIPTION = CaseInsensitivePattern.compile(
    "описаниеошибки|errordescription"
  );
  private static final Pattern PATTERN_EVENT_LOG_LEVEL = CaseInsensitivePattern.compile(
    "уровеньжурналарегистрации|eventloglevel"
  );
  private static final Pattern PATTERN_ERROR = CaseInsensitivePattern.compile(
    "ошибка|error"
  );

  private static final int WRITE_LOG_EVENT_METHOD_PARAMS_COUNT = 5;

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (!checkMethodName(ctx)) {
      return super.visitGlobalMethodCall(ctx);
    }

    checkParams(ctx);

    return super.visitGlobalMethodCall(ctx);
  }

  private void checkParams(BSLParser.GlobalMethodCallContext ctx) {
    final var callParams = ctx.doCall().callParamList().callParam();
    if (!checkFirstParams(ctx, callParams)){
      return;
    }

    if (isInsideExceptBlock(ctx)) {

      final var logLevelCtx = callParams.get(1);
      if (!hasErrorLogLevel(logLevelCtx)) {
        fireIssue(ctx, "noErrorLogLevelInsideExceptBlock");
        return;
      }

      final var commentCtx = callParams.get(4);
      if (!isCommentCorrect(commentCtx)) {
        fireIssue(ctx, "noDetailErrorDescription");
      }
    }
  }

  private boolean checkFirstParams(BSLParser.GlobalMethodCallContext ctx, List<? extends BSLParser.CallParamContext> callParams) {
    if (callParams.size() < WRITE_LOG_EVENT_METHOD_PARAMS_COUNT) {
      fireIssue(ctx, "wrongNumberMessage");
      return false;
    }

    final BSLParser.CallParamContext secondParamCtx = callParams.get(1);
    if (secondParamCtx.getChildCount() == 0) {
      fireIssue(ctx, "noSecondParameter");
      return false;
    }

    final BSLParser.CallParamContext commentCtx = callParams.get(4);
    if (commentCtx.getChildCount() == 0) {
      fireIssue(ctx, "noComment");
      return false;
    }
    return true;
  }

  private static boolean checkMethodName(BSLParser.GlobalMethodCallContext ctx) {
    return WRITELOGEVENT.matcher(ctx.methodName().getText()).matches();
  }

  private void fireIssue(BSLParser.GlobalMethodCallContext ctx, String messageKey) {
    var diagnosticMessage = info.getResourceString(messageKey);
    diagnosticStorage.addDiagnostic(ctx, diagnosticMessage);
  }

  private static boolean hasErrorLogLevel(BSLParser.CallParamContext callParamContext) {
    final var complexIdentifier = Optional.of(callParamContext)
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .filter(memberContexts -> memberContexts.size() == 1)
      .map(memberContexts -> memberContexts.get(0))
      .map(BSLParser.MemberContext::complexIdentifier)
      .filter(identifier -> identifier.IDENTIFIER() != null);
    if (complexIdentifier.isEmpty()) {
      return true;
    }

    final var result = complexIdentifier
      .filter(identifier -> PATTERN_EVENT_LOG_LEVEL.matcher(identifier.IDENTIFIER().getText()).matches())
      .filter(identifier -> identifier.modifier().size() == 1)
      .map(identifier -> identifier.modifier(0))
      .map(BSLParser.ModifierContext::accessProperty)
      .filter(accessProperty -> PATTERN_ERROR.matcher(accessProperty.IDENTIFIER().getText()).matches())
      .isPresent();
    if (result) {
      return true;
    }
    return complexIdentifier
      .filter(identifier -> identifier.getChildCount() == 1)
      .isPresent();
  }

  private static boolean isCommentCorrect(BSLParser.CallParamContext commentsCtx) {
    var codeBlockContext = (BSLParser.CodeBlockContext) Trees.getRootParent(commentsCtx, BSLParser.RULE_codeBlock);
    if (codeBlockContext == null) {
      return false;
    }
    if (hasRaiseStatement(codeBlockContext)) {
      return true;
    }
    return isValidExpression(codeBlockContext, commentsCtx.expression(), true);
  }

  private static boolean hasRaiseStatement(BSLParser.CodeBlockContext codeBlockContext) {
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

  private static boolean isValidExpression(
    BSLParser.CodeBlockContext codeBlock,
    @Nullable BSLParser.ExpressionContext expression,
    boolean checkPrevAssignment
  ) {
    if (expression == null) {
      return true;
    }
    final var assignmentGlobalCalls = Trees.findAllRuleNodes(expression, BSLParser.RULE_globalMethodCall);
    if (!assignmentGlobalCalls.isEmpty()) {
      if (isErrorDescriptionCallCorrect(assignmentGlobalCalls)) {
        return true;
      }
      if (hasSimpleErrorDescription(assignmentGlobalCalls) || hasBriefErrorDescription(assignmentGlobalCalls)) {
        return false;
      }
    }
    return isValidExpression(expression, codeBlock, checkPrevAssignment);
  }

  private static boolean isErrorDescriptionCallCorrect(Collection<ParseTree> globalCalls) {
    return globalCalls.stream()
      .filter(ctx -> ctx instanceof BSLParser.GlobalMethodCallContext)
      .map(BSLParser.GlobalMethodCallContext.class::cast)
      .filter(ctx -> isAppropriateName(ctx, PATTERN_DETAIL_ERROR_DESCRIPTION))
      .anyMatch(UsageWriteLogEventDiagnostic::hasFirstDescendantGlobalCall);
  }

  private static boolean isAppropriateName(
    BSLParser.GlobalMethodCallContext ctx,
    Pattern patternDetailErrorDescription
  ) {
    return patternDetailErrorDescription.matcher(ctx.methodName().getText()).matches();
  }

  private static boolean hasFirstDescendantGlobalCall(BSLParser.GlobalMethodCallContext globalCallCtx) {
    return Trees.findAllRuleNodes(globalCallCtx, BSLParser.RULE_globalMethodCall).stream()
      .map(BSLParser.GlobalMethodCallContext.class::cast)
      .anyMatch(ctx -> isAppropriateName(ctx, PATTERN_ERROR_INFO));
  }

  private static boolean hasSimpleErrorDescription(Collection<ParseTree> globalCalls) {
    return globalCalls.stream()
      .filter(ctx -> ctx instanceof BSLParser.GlobalMethodCallContext)
      .anyMatch(ctx -> isAppropriateName((BSLParser.GlobalMethodCallContext) ctx, PATTERN_SIMPLE_ERROR_DESCRIPTION));
  }

  private static boolean hasBriefErrorDescription(Collection<ParseTree> globalCalls) {
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
    if (!isInsideExceptBlock(codeBlock) && ctx.constValue() != null) {
      return true;
    }
    if (checkPrevAssignment) {
      final var complexIdentifier = ctx.complexIdentifier();
      if (complexIdentifier != null) {
        return isValidVarAssignment(complexIdentifier, codeBlock);
      }
    }
    return false;
  }

  private static boolean isValidVarAssignment(
    BSLParser.ComplexIdentifierContext identifierContext,
    BSLParser.CodeBlockContext codeBlock
  ) {
    String varName = identifierContext.getText();
    return getAssignment(varName, codeBlock)
      .map(BSLParser.AssignmentContext::expression)
      .map(expression -> isValidExpression(codeBlock, expression, false))
      .orElse(true);
  }

  private static Optional<BSLParser.AssignmentContext> getAssignment(
    String varName,
    BSLParser.CodeBlockContext codeBlock
  ) {
    return Optional.of(codeBlock)
      .map(BSLParser.CodeBlockContext::statement)
      .filter(statementContexts -> !statementContexts.isEmpty())
      .map(statementContexts -> codeBlock.statement(0))
      .map(BSLParser.StatementContext::assignment)
      .filter(assignmentContext -> assignmentContext.lValue().getText().equalsIgnoreCase(varName));
  }

  private static boolean isInsideExceptBlock(BSLParserRuleContext ctx) {
    return Trees.getRootParent(ctx, BSLParser.RULE_exceptCodeBlock) != null;
  }

}
