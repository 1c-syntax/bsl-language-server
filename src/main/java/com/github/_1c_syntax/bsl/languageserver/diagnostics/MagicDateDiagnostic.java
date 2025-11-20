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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTree;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.BRAINOVERLOAD
  }

)
public class MagicDateDiagnostic extends AbstractMagicValueDiagnostic {

  private static final String DEFAULT_AUTHORIZED_DATES = "00010101,00010101000000,000101010000";

  private static final Pattern METHOD_PATTERN = CaseInsensitivePattern.compile(
    "Дата|Date"
  );

  private static final Pattern PARAM_PATTERN = CaseInsensitivePattern.compile(
    "\"[0123]{1}\\d{7}\"|\"[0123]{1}\\d{13}\""
  );
  private static final Pattern ZERO_PATTERN = Pattern.compile("^0+");

  private static final Pattern NON_NUMBER_PATTERN = CaseInsensitivePattern.compile(
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

      if (isInsideDefaultValue(ctx)) {
        return defaultResult();
      }

      final var expressionContext = getExpression(ctx);
      if (expressionContext.isPresent() && insideStructureOrCorrespondence(expressionContext.get())) {
        return defaultResult();
      }
      var expr = expressionContext.orElse(null);
      if (shouldAddDiagnostic(expr)) {
        diagnosticStorage.addDiagnostic(ctx, info.getMessage(ctx.getText()));
      }
    }

    return defaultResult();
  }

  private static boolean isInsideDefaultValue(BSLParser.ConstValueContext ctx) {
    var current = ctx.getParent();
    while (current != null) {
      if (current instanceof BSLParser.DefaultValueContext) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private static boolean shouldAddDiagnostic(BSLParser.ExpressionContext expressionContext) {
    return !insideSimpleAssignment(expressionContext)
      && !insideReturnStatement(expressionContext)
      && !insideAssignmentWithDateMethodForSimpleDate(expressionContext);
  }

  private static boolean isValidDate(BSLParser.StringContext ctx) {
    final var text = ctx.getText();
    if (!PARAM_PATTERN.matcher(text).matches()) {
      return false;
    }
    var strDate = text.substring(1, text.length() - 1);
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
    String s = ZERO_PATTERN.matcher(text).replaceAll("");
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
    String s = NON_NUMBER_PATTERN.matcher(text).replaceAll("");
    return authorizedDates.contains(s);
  }

  private static boolean insideAssignmentWithDateMethodForSimpleDate(BSLParser.ExpressionContext expression) {
    if (expression == null) {
      return false;
    }
    var callParam = expression.getParent(); // callParam
    var callParamList = callParam.getParent(); // callParamList
    if (callParamList.getChildCount() != 1) {
      return false;
    }
    var doCall = callParamList.getParent(); // doCall
    var globalCall = doCall.getParent(); // globalCall - метод Дата(ХХХ)
    if (!(globalCall instanceof BSLParser.GlobalMethodCallContext globalMethodCall)) {
      return false;
    }
    if (!METHOD_PATTERN.matcher(globalMethodCall.methodName().getText()).matches()) {
      return false;
    }
    var complexId = globalCall.getParent(); // complexId
    var member = complexId.getParent(); // member
    var expr = member.getParent(); // expression
    if (expr.getChildCount() != 1) {
      return false;
    }
    var assignment = expr.getParent();
    return assignment instanceof BSLParser.AssignmentContext;
  }
}
