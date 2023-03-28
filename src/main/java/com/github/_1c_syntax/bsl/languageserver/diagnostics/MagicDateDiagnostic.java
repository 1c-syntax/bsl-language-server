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
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

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
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    Optional.of(ctx)
      .filter(it -> methodPattern.matcher(it.methodName().getText()).matches())
      .map(BSLParser.GlobalMethodCallContext::doCall)
      .map(BSLParser.DoCallContext::callParamList)
      .filter(callParamList -> paramPattern.matcher(callParamList.getText()).matches())
      .ifPresent(this::checkExclAddDiagnostic);

    return super.visitGlobalMethodCall(ctx);
  }

  @Override
  public ParseTree visitConstValue(BSLParser.ConstValueContext ctx) {
    TerminalNode tNode = ctx.DATETIME();
    if (tNode != null) {
      checkExclAddDiagnostic(ctx);
    }

    return ctx;
  }

  private void checkExclAddDiagnostic(BSLParserRuleContext ctx) {
    String checked = ctx.getText();
    if (checked != null && !isExcluded(checked)) {
      ParserRuleContext expression;
      if (ctx instanceof BSLParser.CallParamListContext) {
        expression = ctx.getParent().getParent().getParent().getParent().getParent();
      } else {
        expression = ctx.getParent().getParent();
      }
      if (expression instanceof BSLParser.ExpressionContext
        && (!isAssignExpression((BSLParser.ExpressionContext) expression))) {
        diagnosticStorage.addDiagnostic(ctx.stop, info.getMessage(checked));
      }
    }
  }

  private boolean isExcluded(String sIn) {
    String s = nonNumberPattern.matcher(sIn).replaceAll("");
    return authorizedDates.contains(s);
  }

  private static boolean isAssignExpression(BSLParser.ExpressionContext expression) {
    return (expression.getChildCount() <= 1);
  }

}
