/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class UsingHardcodeSecretInformationDiagnostic extends AbstractVisitorDiagnostic {

  private static final String FIND_WORD_DEFAULT = "Пароль|Password";

  private static final Pattern PATTERN_NEW_EXPRESSION = CaseInsensitivePattern.compile(
    "Структура|Structure|Соответствие|Map|FTPСоединение|FTPConnection|HTTPСоединение|HTTPConnection"
  );

  private static final Pattern PATTERN_NEW_EXPRESSION_CONNECTION = CaseInsensitivePattern.compile(
    "FTPСоединение|FTPConnection|HTTPСоединение|HTTPConnection"
  );

  private static final Pattern PATTERN_METHOD_INSERT = CaseInsensitivePattern.compile(
    "Вставить|Insert"
  );

  private static final Pattern PATTERN_CHECK_PASSWORD = Pattern.compile("^[\\*]+$", Pattern.UNICODE_CASE);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = FIND_WORD_DEFAULT
  )
  private Pattern searchWords = getPatternSearch(FIND_WORD_DEFAULT);

  @Override
  public void configure(Map<String, Object> configuration) {
    String searchWordsProperty = (String) configuration.getOrDefault("searchWords", FIND_WORD_DEFAULT);
    searchWords = getPatternSearch(searchWordsProperty);
  }

  private static Pattern getPatternSearch(String value) {
    return CaseInsensitivePattern.compile(
      "^(" + value + ")$"
    );
  }

  /**
   * Проверяем переменные, имена которых есть в ключевых словах поиска (searchWords)
   * на присваивание непустой строки.
   * Пример кода:
   * Пароль = "12345";
   */
  @Override
  public ParseTree visitAssignment(BSLParser.AssignmentContext ctx) {
    var matcher = searchWords.matcher(ctx.getStart().getText());
    if (matcher.find()) {
      List<Token> list = ctx.expression().getTokens();
      if (list.size() == 1 && isNotEmptyStringByToken(list.get(0))) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    }
    return super.visitAssignment(ctx);
  }

  /**
   * Проверяем строковой индекс в [] на присутствие в ключевых словах поиска (searchWords)
   * и на присваивание непустой строки.
   * Пример:
   * Структура["Пароль"] = "12345";
   */
  @Override
  public ParseTree visitAccessIndex(BSLParser.AccessIndexContext ctx) {

    if (parentIsModifierContext(ctx)) {
      return super.visitAccessIndex(ctx);
    }

    List<Token> list = ctx.expression().getTokens();
    if (list.size() == 1) {
      processCheckAssignmentKey(ctx, list.get(0).getText());
    }
    return super.visitAccessIndex(ctx);
  }

  /**
   * Проверяем имя свойства объекта на присутствие в ключевых словах поиска (searchWords)
   * и на присваивание непустой строки.
   * Пример:
   * Структура.Пароль = "12345";
   */
  @Override
  public ParseTree visitAccessProperty(BSLParser.AccessPropertyContext ctx) {

    if (parentIsModifierContext(ctx)) {
      return super.visitAccessProperty(ctx);
    }

    processCheckAssignmentKey(ctx, ctx.getStop().getText());
    return super.visitAccessProperty(ctx);
  }

  /**
   * Проверяем использования метода "Вставить" и имя ключа на присутствие в ключевых словах поиска
   * (searchWords) и на присваивание непустой строки.
   * Пример:
   * Структура.Вставить("Пароль", "12345");
   */
  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    var matcherMethod = PATTERN_METHOD_INSERT.matcher(ctx.methodName().getText());
    if (matcherMethod.find()) {
      List<? extends BSLParser.CallParamContext> list = ctx.doCall().callParamList().callParam();
      var matcher = searchWords.matcher(getClearString(list.get(0).getText()));
      if (matcher.find() && list.size() > 1 && isNotEmptyStringByToken(list.get(1).getStart())) {
        addDiagnosticByAssignment(ctx, BSLParser.RULE_statement);
      }
    }
    return super.visitMethodCall(ctx);
  }

  /**
   * Проверяем имя ключа на присутствие в ключевых словах поиска и его значения на непустую строку.
   * Пример:
   * Структура = Новый Структура("Пароль", "12345");
   */
  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    var typeNameContext = ctx.typeName();
    if (typeNameContext == null) {
      return super.visitNewExpression(ctx);
    }
    var matcherTypeName = PATTERN_NEW_EXPRESSION.matcher(typeNameContext.getText());
    if (matcherTypeName.find()) {
      var doCallContext = ctx.doCall();
      if (doCallContext != null) {
        List<? extends BSLParser.CallParamContext> list = doCallContext.callParamList().callParam();
        if (!list.isEmpty()) {
          processCheckNewExpression(ctx, list, typeNameContext.getText());
        }
      }
    }
    return super.visitNewExpression(ctx);
  }

  private void processCheckNewExpression(BSLParser.NewExpressionContext ctx,
                                         List<? extends BSLParser.CallParamContext> list, String typeName) {
    var matcherTypeNameConnection = PATTERN_NEW_EXPRESSION_CONNECTION.matcher(typeName);
    if (matcherTypeNameConnection.find()) {
      if (list.size() >= 4 && isNotEmptyStringByToken(list.get(3).getStart())) {
        addDiagnosticByAssignment(ctx, BSLParser.RULE_assignment);
      }
    } else {
      processParameterList(ctx, list);
    }
  }

  private void processCheckAssignmentKey(ParserRuleContext ctx, String accessText) {
    var matcher = searchWords.matcher(getClearString(accessText));
    if (matcher.find()) {
      var assignment = Trees.getAncestorByRuleIndex(
        ctx,
        BSLParser.RULE_assignment
      );
      if (assignment != null
        && ((BSLParser.AssignmentContext) assignment).expression().getChildCount() == 1
        && isNotEmptyStringByToken(assignment.getStop())) {
        diagnosticStorage.addDiagnostic(assignment, info.getMessage());
      }
    }
  }

  private void processParameterList(
    BSLParser.NewExpressionContext ctx,
    List<? extends BSLParser.CallParamContext> list
  ) {
    String[] arr = list.get(0).getText().split(",");
    for (var index = 0; index < arr.length; index++) {
      var matcher = searchWords.matcher(getClearString(arr[index]));
      if (matcher.find() && list.size() > index + 1 && isNotEmptyStringByToken(list.get(index + 1).getStart())) {
        addDiagnosticByAssignment(ctx, BSLParser.RULE_assignment);
        break;
      }
    }
  }

  private void addDiagnosticByAssignment(ParserRuleContext ctx, int type) {
    var assignment = Trees.getAncestorByRuleIndex(ctx, type);
    if (assignment != null) {
      diagnosticStorage.addDiagnostic(assignment, info.getMessage());
    }
  }

  private static boolean isNotEmptyStringByToken(Token token) {
    boolean result = token.getType() == BSLParser.STRING && token.getText().length() > 2;
    if (result) {
      boolean foundStars = PATTERN_CHECK_PASSWORD.matcher(token.getText().substring(1, token.getText().length() - 1)).find();
      if (foundStars) {
        result = false;
      }
    }
    return result;
  }

  private static String getClearString(String inputString) {
    return inputString.replace("\"", "").replace(" ", "");
  }

  private static boolean parentIsModifierContext(ParserRuleContext ctx) {
    return ctx.getParent() instanceof BSLParser.ModifierContext;
  }

}
