
/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import javax.annotation.CheckForNull;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  minutesToFix = 15
)
public class UsingHardcodeSecretInformationDiagnostic extends AbstractVisitorDiagnostic {

  private static final String FIND_WORD_DEFAULT = "Пароль|Password";

  private static final Pattern patternNewExpression = Pattern.compile(
    "Структура|Structure|Соответствие|Map",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern patternMethodInsert = Pattern.compile(
    "Вставить|Insert",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + FIND_WORD_DEFAULT,
    description = "Ключевые слова поиска конфиденциальной информации в переменных, структурах, соответствиях."
  )
  private String searchWords = FIND_WORD_DEFAULT;

  private Pattern pattern = getPatternSearch(FIND_WORD_DEFAULT);

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    searchWords = (String) configuration.get("searchWords");
    pattern = getPatternSearch(searchWords);
  }

  private static Pattern getPatternSearch(String value) {
    return Pattern.compile(
      "^(" + value + ")",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  @Override
  public ParseTree visitAssignment(BSLParser.AssignmentContext ctx) {
    Matcher matcher = pattern.matcher(ctx.getStart().getText());
    if (matcher.find()) {
      List<Token> list = ctx.expression().getTokens();
      if (list.size() == 1 && isNotEmptyStringByToken(list.get(0))) {
        diagnosticStorage.addDiagnostic(ctx, getDiagnosticMessage());
      }
    }
    return super.visitAssignment(ctx);
  }

  @Override
  public ParseTree visitAccessIndex(BSLParser.AccessIndexContext ctx) {
    List<Token> list = ctx.getTokens();
    if (!list.isEmpty()) {
      processCheckAssignmentKey(ctx, list.get(0).getText());
    }
    return super.visitAccessIndex(ctx);
  }

  @Override
  public ParseTree visitAccessProperty(BSLParser.AccessPropertyContext ctx) {
    processCheckAssignmentKey(ctx, ctx.getStop().getText());
    return super.visitAccessProperty(ctx);
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    Matcher matcherMethod = patternMethodInsert.matcher(ctx.methodName().getText());
    if (matcherMethod.find()) {
      List<BSLParser.CallParamContext> list = ctx.doCall().callParamList().callParam();
      Matcher matcher = pattern.matcher(getClearString(list.get(0).getText()));
      if (matcher.find() && list.size() > 1 && isNotEmptyStringByToken(list.get(1).getStart())) {
        addDiagnosticByAssignment(ctx, BSLParser.RULE_statement);
      }
    }
    return super.visitMethodCall(ctx);
  }

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    BSLParser.TypeNameContext typeNameContext = ctx.typeName();
    if (typeNameContext == null) {
      return super.visitNewExpression(ctx);
    }

    Matcher matcherTypeName = patternNewExpression.matcher(typeNameContext.getText());
    if (matcherTypeName.find()) {
      BSLParser.DoCallContext doCallContext = ctx.doCall();
      if (doCallContext != null) {
        List<BSLParser.CallParamContext> list = doCallContext.callParamList().callParam();
        if (!list.isEmpty()) {
          processParameterList(ctx, list);
        }
      }
    }
    return super.visitNewExpression(ctx);
  }

  private void processCheckAssignmentKey(BSLParserRuleContext ctx, String accessText) {
    Matcher matcher = pattern.matcher(getClearString(accessText));
    if (matcher.find()) {
      ParserRuleContext assignment = getAncestorByRuleIndex((ParserRuleContext) ctx.getRuleContext(), BSLParser.RULE_assignment);
      if (assignment != null && isNotEmptyStringByToken(assignment.getStop())) {
        diagnosticStorage.addDiagnostic((BSLParser.AssignmentContext) assignment, getDiagnosticMessage());
      }
    }
  }

  private void processParameterList(BSLParser.NewExpressionContext ctx, List<BSLParser.CallParamContext> list) {
    String[] arr = list.get(0).getText().split(",");
    for (int index = 0; index < arr.length; index++) {
      Matcher matcher = pattern.matcher(getClearString(arr[index]));
      if (matcher.find() && list.size() > index + 1 && isNotEmptyStringByToken(list.get(index + 1).getStart())) {
        addDiagnosticByAssignment(ctx, BSLParser.RULE_assignment);
        break;
      }
    }
  }

  private void addDiagnosticByAssignment(BSLParserRuleContext ctx, int type) {
    ParserRuleContext assignment = getAncestorByRuleIndex((ParserRuleContext) ctx.getRuleContext(), type);
    if (assignment != null) {
      diagnosticStorage.addDiagnostic((BSLParserRuleContext) assignment, getDiagnosticMessage());
    }
  }

  private static boolean isNotEmptyStringByToken(Token token) {
    return token.getType() == BSLParser.STRING && !(token.getText().length() == 2);
  }

  private static String getClearString(String inputString) {
    return inputString.replace("\"", "").replace(" ", "");
  }

  // TODO: перенести в bsl parser
  @CheckForNull
  private static ParserRuleContext getAncestorByRuleIndex(ParserRuleContext element, int type) {
    ParserRuleContext parent = element.getParent();
    if (parent == null) {
      return null;
    }
    if (parent.getRuleIndex() == type) {
      return parent;
    }
    return getAncestorByRuleIndex(parent, type);
  }

}