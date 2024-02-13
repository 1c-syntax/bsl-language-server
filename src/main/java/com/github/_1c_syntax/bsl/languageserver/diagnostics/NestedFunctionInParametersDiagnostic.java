/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import edu.umd.cs.findbugs.annotations.Nullable;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.BADPRACTICE
  }
)
public class NestedFunctionInParametersDiagnostic extends AbstractVisitorDiagnostic {

  private static final boolean DEFAULT_ALLOW_ONELINER = true;
  private static final String ALLOWED_METHOD_NAMES = "НСтр,NStr,ПредопределенноеЗначение,PredefinedValue";

  private Pattern allowedMethodNamesPattern = compilePattern(ALLOWED_METHOD_NAMES);

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_ALLOW_ONELINER
  )
  private boolean allowOneliner = DEFAULT_ALLOW_ONELINER;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = ALLOWED_METHOD_NAMES
  )
  private String allowedMethodNames = ALLOWED_METHOD_NAMES;

  private static Pattern compilePattern(String allowedNames) {
    return CaseInsensitivePattern.compile(
      "^(" + allowedNames.replace(" ", "").replace(",", "|") + ")");
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    checkMethodCall(ctx, ctx.doCall(), ctx.methodName());
    return super.visitMethodCall(ctx);
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    super.configure(configuration);
    allowedMethodNamesPattern = compilePattern(allowedMethodNames);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    checkMethodCall(ctx, ctx.doCall(), ctx.methodName());
    return super.visitGlobalMethodCall(ctx);
  }

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {

    if (!findNestedCall(ctx, ctx.doCall())) {
      return super.visitNewExpression(ctx);
    }

    if (ctx.typeName() != null) {
      diagnosticStorage.addDiagnostic(
        ctx.typeName(),
        info.getMessage(
          info.getResourceString("diagnosticMessageConstructor"),
          ctx.typeName().getText()));
    } else { // для конструкторов через параметр New
      diagnosticStorage.addDiagnostic(
        ctx.getStart(),
        info.getResourceString("diagnosticMessageWithoutName",
          info.getResourceString("diagnosticMessageConstructor")));
    }

    return super.visitNewExpression(ctx);
  }

  private boolean findNestedCall(BSLParserRuleContext ctx, BSLParser.DoCallContext ctxDoCall) {
    // однострочники пропускаем сразу
    if (ctx.getStart().getLine() == ctx.getStop().getLine()) {
      return false;
    }

    // пропускаем с пустым списком параметров вызова
    if (emptyCallParameterList(ctxDoCall)) {
      return false;
    }

    // вложенные вызовы есть, если среди них нет запрещенных, то посчитаем, что все хорошо
    return containsForbiddenMethod(ctxDoCall)
      && multilineParam(ctxDoCall);
  }

  private boolean multilineParam(BSLParser.DoCallContext ctxDoCall) {
    return !allowOneliner || ctxDoCall.callParamList().callParam().stream()
      .anyMatch(callParamContext -> callParamContext.getStop().getLine() > callParamContext.getStart().getLine());
  }

  private void checkMethodCall(BSLParserRuleContext ctx,
                               BSLParser.DoCallContext ctxDoCall,
                               BSLParser.MethodNameContext ctxMethodName) {
    if (findNestedCall(ctx, ctxDoCall)) {

      diagnosticStorage.addDiagnostic(ctxMethodName,
        info.getMessage(
          info.getResourceString("diagnosticMessageMethod"),
          ctxMethodName.getText()));
    }
  }

  private boolean containsForbiddenMethod(ParseTree t) {
    var needReturn = false;
    if (t instanceof ParserRuleContext) {
      if (BSLParser.RULE_methodCall == ((ParserRuleContext) t).getRuleIndex()) {
        needReturn = true;
      } else if (BSLParser.RULE_newExpression == ((ParserRuleContext) t).getRuleIndex()) {
        needReturn = !emptyCallParameterList(((BSLParser.NewExpressionContext) t).doCall());
      } else if (BSLParser.RULE_globalMethodCall == ((ParserRuleContext) t).getRuleIndex()) {
        needReturn = !allowedMethodNamesPattern.matcher(
          ((BSLParser.GlobalMethodCallContext) t).methodName().getText()).matches();
      } else {
        // no-op
      }
    }

    if (needReturn) {
      return true;
    }

    return IntStream.range(0, t.getChildCount())
      .anyMatch(i -> containsForbiddenMethod(t.getChild(i)));
  }

  private static boolean emptyCallParameterList(@Nullable BSLParser.DoCallContext ctxDoCall) {
    return ctxDoCall == null
      || ctxDoCall.callParamList() == null
      || ctxDoCall.callParamList().isEmpty();
  }
}
