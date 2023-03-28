/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import edu.umd.cs.findbugs.annotations.Nullable;
import org.antlr.v4.runtime.tree.ParseTree;

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
  public static final int COMMENTS_PARAM_INDEX = 4;
  public static final String NO_ERROR_LOG_LEVEL_INSIDE_EXCEPT_BLOCK = "noErrorLogLevelInsideExceptBlock";
  public static final String NO_DETAIL_ERROR_DESCRIPTION = "noDetailErrorDescription";
  public static final String WRONG_NUMBER_MESSAGE = "wrongNumberMessage";
  public static final String NO_SECOND_PARAMETER = "noSecondParameter";
  public static final String NO_COMMENT = "noComment";

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext context) {

    if (!checkMethodName(context)) {
      return super.visitGlobalMethodCall(context);
    }

    checkParams(context);

    return super.defaultResult();
  }

  private void checkParams(BSLParser.GlobalMethodCallContext context) {
    final var callParams = context.doCall().callParamList().callParam();
    if (!checkFirstParams(context, callParams)){
      return;
    }

    if (isInsideExceptBlock(context)) {

      final var logLevelCtx = callParams.get(1);
      if (!hasErrorLogLevel(logLevelCtx)) {
        fireIssue(context, NO_ERROR_LOG_LEVEL_INSIDE_EXCEPT_BLOCK);
        return;
      }

      final var commentCtx = callParams.get(COMMENTS_PARAM_INDEX);
      if (!isCommentCorrect(commentCtx)) {
        fireIssue(context, NO_DETAIL_ERROR_DESCRIPTION);
      }
    }
  }

  private boolean checkFirstParams(BSLParser.GlobalMethodCallContext context, List<? extends BSLParser.CallParamContext> callParams) {
    if (callParams.size() < WRITE_LOG_EVENT_METHOD_PARAMS_COUNT) {
      fireIssue(context, WRONG_NUMBER_MESSAGE);
      return false;
    }

    final BSLParser.CallParamContext secondParamCtx = callParams.get(1);
    if (secondParamCtx.getChildCount() == 0) {
      fireIssue(context, NO_SECOND_PARAMETER);
      return false;
    }

    final BSLParser.CallParamContext commentCtx = callParams.get(COMMENTS_PARAM_INDEX);
    if (commentCtx.getChildCount() == 0) {
      fireIssue(context, NO_COMMENT);
      return false;
    }
    return true;
  }

  private static boolean checkMethodName(BSLParser.GlobalMethodCallContext context) {
    return WRITELOGEVENT.matcher(context.methodName().getText()).matches();
  }

  private void fireIssue(BSLParser.GlobalMethodCallContext context, String messageKey) {
    var diagnosticMessage = info.getResourceString(messageKey);
    diagnosticStorage.addDiagnostic(context, diagnosticMessage);
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
      .filter(context -> context instanceof BSLParser.GlobalMethodCallContext)
      .map(BSLParser.GlobalMethodCallContext.class::cast)
      .filter(context -> isAppropriateName(context, PATTERN_DETAIL_ERROR_DESCRIPTION))
      .anyMatch(UsageWriteLogEventDiagnostic::hasFirstDescendantGlobalCall);
  }

  private static boolean isAppropriateName(
    BSLParser.GlobalMethodCallContext context,
    Pattern patternDetailErrorDescription
  ) {
    return patternDetailErrorDescription.matcher(context.methodName().getText()).matches();
  }

  private static boolean hasFirstDescendantGlobalCall(BSLParser.GlobalMethodCallContext globalCallCtx) {
    return Trees.findAllRuleNodes(globalCallCtx, BSLParser.RULE_globalMethodCall).stream()
      .map(BSLParser.GlobalMethodCallContext.class::cast)
      .anyMatch(context -> isAppropriateName(context, PATTERN_ERROR_INFO));
  }

  private static boolean hasSimpleErrorDescription(Collection<ParseTree> globalCalls) {
    return globalCalls.stream()
      .filter(context -> context instanceof BSLParser.GlobalMethodCallContext)
      .anyMatch(context -> isAppropriateName((BSLParser.GlobalMethodCallContext) context, PATTERN_SIMPLE_ERROR_DESCRIPTION));
  }

  private static boolean hasBriefErrorDescription(Collection<ParseTree> globalCalls) {
    return globalCalls.stream()
      .filter(context -> context instanceof BSLParser.GlobalMethodCallContext)
      .anyMatch(context -> isAppropriateName((BSLParser.GlobalMethodCallContext) context, PATTERN_BRIEF_ERROR_DESCRIPTION));
  }

  private static boolean isValidExpression(BSLParser.ExpressionContext context, BSLParser.CodeBlockContext codeBlock,
                                           boolean checkPrevAssignment) {
    return context.member().stream()
      .allMatch(memberContext -> isValidExpression(memberContext, codeBlock, checkPrevAssignment));
  }

  private static boolean isValidExpression(BSLParser.MemberContext context, BSLParser.CodeBlockContext codeBlock,
                                           boolean checkPrevAssignment) {
    if (context.constValue() != null) {
      return false;
    }
    if (checkPrevAssignment) {
      final var complexIdentifier = context.complexIdentifier();
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

  private static boolean isInsideExceptBlock(BSLParserRuleContext context) {
    return Trees.getRootParent(context, BSLParser.RULE_exceptCodeBlock) != null;
  }

}
