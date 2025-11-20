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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.antlr.v4.runtime.ParserRuleContext;

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

  // https://regex101.com/r/9segNm/3
  private static final Pattern paramsPattern = Pattern.compile("(?<!%)%(?:(10|[1-9])(?!\\d)|\\((10|[1-9])\\))");
  // https://regex101.com/r/s4y7Nz/2
  private static final Pattern wrongNumbersPattern = Pattern.compile(
    "(?<!%)%(?:(1[1-9]\\d*|[2-9]\\d+|0|10\\d+)(?!\\d)|\\((1[1-9]\\d*|[2-9]\\d+|0|10\\d+)\\))");
  private static final Pattern TWO_PERCENT_PATTERN = Pattern.compile("%%");

  private static final Pattern nstrPattern = CaseInsensitivePattern.compile(
    "(нстр|nstr)"
  );

  public IncorrectUseOfStrTemplateDiagnostic() {
    super(messagePattern);
  }

  private static boolean paramsAreDifferent(BSLParser.GlobalMethodCallContext ctx) {
    var params = ctx.doCall().callParamList().callParam();

    if (params.isEmpty()) {
      return false;
    }

    var templateString = getTemplateString(params.get(0));
    if (templateString == null) {
      return false;
    }
    int usedParamsCount = params.size() - 1;
    return isWrongTemplate(templateString, usedParamsCount);
  }

  private static boolean isWrongTemplate(String templateString, int usedParamsCount) {
    final var isWrongCall = compareTemplateAndParams(templateString, usedParamsCount);
    if (!isWrongCall) {
      return false;
    }
    var str = TWO_PERCENT_PATTERN.matcher(templateString).replaceAll("");
    return compareTemplateAndParams(str, usedParamsCount);
  }

  private static boolean compareTemplateAndParams(String templateString, int usedParamsCount) {
    boolean haveParams = usedParamsCount > 0;

    var matcher = paramsPattern.matcher(templateString);
    boolean matches = matcher.find();

    return (matches && !haveParams)
      || (!matches && haveParams)
      || (matches && variousParams(usedParamsCount, matcher))
      || wrongNumbersPattern.matcher(templateString).find();
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

  private static Optional<String> getStringFromExpression(Optional<BSLParser.ExpressionContext> expressionContext) {
    final var LENGTH_OF_EMPTY_STRING_FROM_AST = 2;
    return getConstValue(expressionContext, true)
      .map(BSLParser.ConstValueContext::string)
      .map(ParserRuleContext::getText)
      .filter(s -> s.length() > LENGTH_OF_EMPTY_STRING_FROM_AST);
  }

  private static Optional<BSLParser.ConstValueContext> getConstValue(Optional<BSLParser.ExpressionContext> expressionContext,
                                                                     boolean isFullSearch) {
    return expressionContext
      .map(BSLParser.ExpressionContext::member)
      .filter(memberContexts -> memberContexts.size() == 1)
      .map(memberContexts -> memberContexts.get(0))
      .flatMap(memberContext -> calcStringForMemberContext(memberContext, isFullSearch));
  }

  private static Optional<BSLParser.ConstValueContext> calcStringForMemberContext(BSLParser.MemberContext memberContext, 
                                                                                  boolean isFullSearch) {
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

  private static Optional<BSLParser.ConstValueContext> calcAssignedValueForIdentifier(
          BSLParser.ComplexIdentifierContext complexIdentifier) {
    
    final var identifier = complexIdentifier.IDENTIFIER();
    if (identifier == null) {
      return Optional.empty();
    }
    final var varName = identifier.getText();

    var prevStatement = (BSLParser.StatementContext) Objects.requireNonNull(Trees.getRootParent(complexIdentifier,
      BSLParser.RULE_statement));
    while (true) {
      prevStatement = (BSLParser.StatementContext) getPreviousNode(Objects.requireNonNull(prevStatement), 
        BSLParser.RULE_statement);
      
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
  private static ParserRuleContext getPreviousNode(ParserRuleContext node, int ruleStatement) {

    final var children = node.getParent().children;
    final var pos = children.indexOf(node);
    if (pos > 0) {
      for (int i = pos - 1; i >= 0; i--) {
        final var prev = (ParserRuleContext) children.get(i);
        if (prev.getRuleIndex() == ruleStatement) {
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
      .filter(callParamListContext -> !callParamListContext.callParam().isEmpty())
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
      var index = Integer.parseInt(group);
      if (index > usedParamsCount) {
        return true;
      }
      templateParams.add(index);
    }
    for (var i = 1; i <= usedParamsCount; i++) {
      if (!templateParams.contains(i)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (super.checkGlobalMethodCall(ctx) && paramsAreDifferent(ctx)) {
      diagnosticStorage.addDiagnostic(ctx);
    }
    return false;

  }
}
