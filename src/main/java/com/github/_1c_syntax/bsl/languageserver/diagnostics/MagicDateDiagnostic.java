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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.ParserRuleContext;
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
    "\"[0123]{1}\\d{7}\"|\"[0123]{1}\\d{13}\""
  );
  private static final Pattern zeroPattern = Pattern.compile("^0+");

  private static final Pattern nonNumberPattern = CaseInsensitivePattern.compile(
    "\\D"
  );
  public static final int MAX_YEAR_BY_1C = 9999;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = DEFAULT_AUTHORIZED_DATES
  )
  private final Set<String> authorizedDates = new HashSet<>(Arrays.asList(DEFAULT_AUTHORIZED_DATES.split(",")));

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
      if (sNode != null && !isValidDate(sNode)) {
        return defaultResult();
      }

      final var expressionContext = getExpression(Optional.of(ctx));
      if (!insideSimpleDateAssignment(expressionContext) && !insideReturnSimpleDate(expressionContext)
        && !insideAssignmentWithDateMethodForSimpleDate(expressionContext)) {
        diagnosticStorage.addDiagnostic(ctx, info.getMessage(ctx.getText()));
      }
    }

    return defaultResult();
  }

  private static boolean isValidDate(BSLParser.StringContext ctx) {
    final var text = ctx.getText();
    if (!paramPattern.matcher(text).matches()) {
      return false;
    }
    var strDate = text.substring(1, text.length() - 1); // убрать кавычки
    return isValidDate(strDate);
  }

  private static boolean isValidDate(String strDate) {
    var year = parseInt(strDate.substring(0, 4));
    if (year < 1 || year > MAX_YEAR_BY_1C) {
      return false;
    }
    var month = parseInt(strDate.substring(4, 6));
    var day = parseInt(strDate.substring(6, 8));
    if (month < 1 || month > 12 || day < 1 || day > 31) {
      return false;
    }
    if (strDate.length() == 8) {
      return true;
    }
    var hh = parseInt(strDate.substring(8, 10));
    var mm = parseInt(strDate.substring(10, 12));
    var ss = parseInt(strDate.substring(12, 14));
    return hh <= 24 && mm <= 60 && ss <= 60;
  }

  private static int parseInt(String text) {
    String s = zeroPattern.matcher(text).replaceAll("");
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private boolean isAccepted(BSLParser.ConstValueContext ctx) {
    String text = ctx.getText();
    return text != null && !text.isEmpty() && !isExcluded(text);
  }

  private boolean isExcluded(String text) {
    String s = nonNumberPattern.matcher(text).replaceAll("");
    return authorizedDates.contains(s);
  }

  private static Optional<BSLParser.ExpressionContext> getExpression(Optional<BSLParser.ConstValueContext> constValue) {
    return constValue
      .map(ParserRuleContext::getParent)
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent)
      .filter(context -> context.getChildCount() == 1)
      .filter(BSLParser.ExpressionContext.class::isInstance)
      .map(BSLParser.ExpressionContext.class::cast);
  }

  private static boolean insideSimpleDateAssignment(Optional<BSLParser.ExpressionContext> expression) {
    return insideContext(expression, BSLParser.AssignmentContext.class);
  }

  private static boolean insideContext(Optional<BSLParser.ExpressionContext> expression,
                                       Class<? extends ParserRuleContext> assignmentContextClass) {
    return expression
      .map(ParserRuleContext::getParent)
      .filter(assignmentContextClass::isInstance)
      .isPresent();
  }

  private static boolean insideReturnSimpleDate(Optional<BSLParser.ExpressionContext> expression) {
    return insideContext(expression, BSLParser.ReturnStatementContext.class);
  }

  private static boolean insideAssignmentWithDateMethodForSimpleDate(Optional<BSLParser.ExpressionContext> expression) {
    return expression
      .map(ParserRuleContext::getParent) // callParam
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent) // callParamList
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent) // doCall
      .map(ParserRuleContext::getParent) // globalCall - метод Дата(ХХХ)
      .filter(BSLParser.GlobalMethodCallContext.class::isInstance)
      .map(BSLParser.GlobalMethodCallContext.class::cast)
      .filter(context -> methodPattern.matcher(context.methodName().getText()).matches())
      .map(ParserRuleContext::getParent) // complexId
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent) // member
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent) // expression
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent)
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .isPresent();
  }
}
