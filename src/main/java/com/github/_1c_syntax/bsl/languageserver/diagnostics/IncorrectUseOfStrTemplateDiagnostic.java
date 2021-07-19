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
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNPREDICTABLE
  }

)

public class IncorrectUseOfStrTemplateDiagnostic extends AbstractFindMethodDiagnostic {

  private static final Pattern messagePattern = CaseInsensitivePattern.compile(
    "(стршаблон|strtemplate)"
  );

  // https://regex101.com/r/9segNm/1
  private static final Pattern paramsPattern = Pattern.compile("(?<!%)%(?:(10|[1-9])(?!\\d)|\\((10|[1-9])\\)\\d)");

  private static final Pattern nstrPattern = CaseInsensitivePattern.compile(
    "(нстр|nstr)"
  );

  public IncorrectUseOfStrTemplateDiagnostic() {
    super(messagePattern);
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

    if (paramsAreDifferent(ctx)) {
      diagnosticStorage.addDiagnostic(ctx);
    }

    return false;
  }

  private static boolean paramsAreDifferent(BSLParser.GlobalMethodCallContext ctx) {
    var params = ctx.doCall().callParamList().callParam();

    if (params.isEmpty()) {
      return false;
    }
    int usedParamsCount = params.size() - 1;
    boolean haveParams = usedParamsCount > 0;

    final String templateString = getTemplateString(params.get(0));
    if (templateString == null) {
      return false;
    }

    var matcher = paramsPattern.matcher(templateString);
    boolean matches = matcher.find();

    return matches && !haveParams
      || !matches && haveParams
      || matches && variousParams(usedParamsCount, matcher);
  }

  @Nullable
  private static String getTemplateString(BSLParser.CallParamContext context) {

    final var ctx = Optional.of(context);
    final var templateStringBefore = getString(ctx)
      .orElseGet(() -> getStringFromNStrCall(ctx)
        .orElse(""));
    if (templateStringBefore.isEmpty()) {
      return null;
    }

    return templateStringBefore.substring(1, templateStringBefore.length() - 1);

  }

  private static Optional<String> getString(Optional<BSLParser.CallParamContext> ctx) {

    final var expressionContext = ctx
      .map(BSLParser.CallParamContext::expression);
    return getStringFromExpression(expressionContext);
  }

  @NotNull
  private static Optional<String> getStringFromExpression(Optional<BSLParser.ExpressionContext> expressionContext) {
    return getConstValue(expressionContext, true)
      .map(BSLParser.ConstValueContext::string)
      .map(BSLParserRuleContext::getText)
      .filter(s -> s.length() > 2);
  }

  @NotNull
  private static Optional<BSLParser.ConstValueContext> getConstValue(Optional<BSLParser.ExpressionContext> expressionContext, boolean isFullSearch) {
    return expressionContext
      .map(BSLParser.ExpressionContext::member)
      .filter(memberContexts -> memberContexts.size() == 1)
      .map(memberContexts -> memberContexts.get(0))
      .flatMap(memberContext -> calcStringForMemberContext(memberContext, isFullSearch));
  }

  private static Optional<BSLParser.ConstValueContext> calcStringForMemberContext(BSLParser.MemberContext memberContext, boolean isFullSearch) {
    final var constValue = memberContext.constValue();
    if (constValue != null) {
      return Optional.of(constValue);
    }
    if (isFullSearch) {
      final var complexIdentifier = memberContext.complexIdentifier();
      if (complexIdentifier != null) {
        return calcAssignedValueForIdentifier(complexIdentifier);
      }
    }
    return Optional.empty();
  }

  private static Optional<BSLParser.ConstValueContext> calcAssignedValueForIdentifier(BSLParser.ComplexIdentifierContext complexIdentifier) {
    final var identifier = complexIdentifier.IDENTIFIER();
    if (identifier == null) {
      return Optional.empty();
    }
    final var varName = identifier.getText();

    var prevStatement = (BSLParser.StatementContext) Objects.requireNonNull(Trees.getRootParent(complexIdentifier,
      BSLParser.RULE_statement));
    while (true) {
      prevStatement = (BSLParser.StatementContext) getPreviousNode(Objects.requireNonNull(prevStatement), BSLParser.RULE_statement);
      if (prevStatement == null) {
        break;
      }
      final var constValueContext = Optional.ofNullable(prevStatement.assignment())
        .filter(assignment -> isAssignmentForVar(varName, assignment))
        .map(BSLParser.AssignmentContext::expression)
        .flatMap(expression -> getConstValue(Optional.of(expression), false));
      if (constValueContext.isPresent()) {
        return constValueContext;
      }
    }
    return Optional.empty();
  }

  @Nullable
  private static BSLParserRuleContext getPreviousNode(BSLParserRuleContext node, int rule_statement) {

    final var children = node.getParent().children;
    final var pos = children.indexOf(node);
    if (pos > 0) {
      for (int i = pos - 1; i >= 0; i--) {
        final var prev = (BSLParserRuleContext) children.get(i);
        if (prev.getRuleIndex() == rule_statement) {
          return prev;
        }
      }
    }

    return null;
  }

  private static boolean isAssignmentForVar(String varName, BSLParser.AssignmentContext assignment) {
    final var lValue = assignment.lValue();
    if (lValue == null) {
      return false;
    }
    final var identifier = lValue.IDENTIFIER();
    return identifier != null && identifier.getText().equalsIgnoreCase(varName);
  }

  private static Optional<String> getStringFromNStrCall(Optional<BSLParser.CallParamContext> ctx) {

    final var nstrCallParamCtx = ctx
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .filter(memberContexts -> memberContexts.size() == 1)
      .map(memberContexts -> memberContexts.get(0))
      .map(BSLParser.MemberContext::complexIdentifier)
      .map(BSLParser.ComplexIdentifierContext::globalMethodCall)
      .filter(globalMethodCallContext -> nstrPattern.matcher(globalMethodCallContext.methodName().getText()).matches())
      .map(BSLParser.GlobalMethodCallContext::doCall)
      .map(BSLParser.DoCallContext::callParamList)
      .filter(callParamListContext -> callParamListContext.callParam().size() >= 1)
      .map(callParamContexts -> callParamContexts.callParam(0));

    return getString(nstrCallParamCtx);
  }

  private static boolean variousParams(int usedParamsCount, Matcher matcher) {
    final var templateParams = new HashSet<Integer>();
    matcher.reset();
    while (matcher.find()) {
      String group = matcher.group(1);
      // может быть null в случае %(цифра)цифра
      if (group == null) {
        group = matcher.group(2);
      }
      final int index = Integer.parseInt(group);
      if (index > usedParamsCount) {
        return true;
      }
      templateParams.add(index);
    }
    for (int i = 1; i <= usedParamsCount; i++) {
      if (!templateParams.contains(i)) {
        return true;
      }
    }
    return false;
  }
}
