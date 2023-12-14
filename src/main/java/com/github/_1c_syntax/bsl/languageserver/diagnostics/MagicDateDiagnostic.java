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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.BRAINOVERLOAD
  }

)
public class MagicDateDiagnostic extends AbstractVisitorDiagnostic {

  private static final String DEFAULT_AUTHORIZED_DATES = "00010101,00010101000000,000101010000";

  private static final Pattern methodPattern = CaseInsensitivePattern.compile(
    "Дата|Date"
  );

  private static final Pattern paramPattern = CaseInsensitivePattern.compile(
    "\"\\d{8}.*"
  );

  private static final Pattern nonNumberPattern = CaseInsensitivePattern.compile(
    "\\D"
  );

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_AUTHORIZED_DATES
  )
  private final Set<String> authorizedDates = new HashSet<>(Arrays.asList(DEFAULT_AUTHORIZED_DATES.split(",")));

  private static Optional<BSLParserRuleContext> getExpression(Optional<BSLParser.ConstValueContext> contextOptional) {
    return contextOptional
      .map(BSLParserRuleContext::getParent)
      .filter(context -> context.getChildCount() == 1)
      .map(BSLParserRuleContext::getParent)
      .filter(context -> context.getChildCount() == 1)
      .filter(BSLParser.ExpressionContext.class::isInstance);
  }

  private static boolean insideSimpleDateAssignment(Optional<BSLParserRuleContext> expressionContext) {
    return expressionContext
      .map(BSLParserRuleContext::getParent)
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .isPresent();
  }

  private static boolean insideAssignmentWithDateMethodForSimpleDate(Optional<BSLParserRuleContext> expressionContext) {
    return expressionContext
      .map(BSLParserRuleContext::getParent) // callParam
      .filter(context -> context.getChildCount() == 1)
      .map(BSLParserRuleContext::getParent) // callParamList
      .filter(context -> context.getChildCount() == 1)
      .map(BSLParserRuleContext::getParent) // doCall
      .map(BSLParserRuleContext::getParent) // globalCall - метод Дата(ХХХ)
      .filter(BSLParser.GlobalMethodCallContext.class::isInstance)
      .map(BSLParser.GlobalMethodCallContext.class::cast)
      .filter(context -> methodPattern.matcher(context.methodName().getText()).matches())
      .map(BSLParserRuleContext::getParent) // complexId
      .filter(context -> context.getChildCount() == 1)
      .map(BSLParserRuleContext::getParent) // member
      .filter(context -> context.getChildCount() == 1)
      .map(BSLParserRuleContext::getParent) // expression
      .filter(context -> context.getChildCount() == 1)
      .map(BSLParserRuleContext::getParent)
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .isPresent();
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    var authorizedDatesString = (String) configuration.getOrDefault("authorizedDates", DEFAULT_AUTHORIZED_DATES);
    Set<String> authD = Arrays.stream(authorizedDatesString.split(","))
      .map(String::trim)
      .collect(Collectors.toSet());
    authorizedDates.clear();
    authorizedDates.addAll(authD);
  }

  @Override
  public ParseTree visitConstValue(BSLParser.ConstValueContext ctx) {
    var tNode = ctx.DATETIME();
    var sNode = ctx.string();
    if ((tNode != null || sNode != null) && isAccepted(ctx)) {
      if (sNode != null && !paramPattern.matcher(ctx.getText()).matches()) {
        return defaultResult();
      }

      final var expressionContext = getExpression(Optional.of(ctx));
      if (!insideSimpleDateAssignment(expressionContext)
        && !insideAssignmentWithDateMethodForSimpleDate(expressionContext)) {
        diagnosticStorage.addDiagnostic(ctx, info.getMessage(ctx.getText()));
      }
    }

    return defaultResult();
  }

  private boolean isAccepted(BSLParser.ConstValueContext ctx) {
    String text = ctx.getText();
    return text != null && !text.isEmpty() && !isExcluded(text);
  }

  private boolean isExcluded(String text) {
    String s = nonNumberPattern.matcher(text).replaceAll("");
    return authorizedDates.contains(s);
  }
}
