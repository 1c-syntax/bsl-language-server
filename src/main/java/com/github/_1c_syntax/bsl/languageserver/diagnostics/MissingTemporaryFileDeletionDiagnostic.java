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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.STANDARD
  }
)

public class MissingTemporaryFileDeletionDiagnostic extends AbstractVisitorDiagnostic {

  private static final String REGEX_DELETION_FILE =
    "УдалитьФайлы|DeleteFiles|НачатьУдалениеФайлов|BeginDeletingFiles|ПереместитьФайл|MoveFile";

  private static final Pattern GET_TEMP_FILE_NAME_PATTERN = CaseInsensitivePattern.compile(
    "^(ПолучитьИмяВременногоФайла|GetTempFileName)"
  );

  @DiagnosticParameter(
    type = String.class,
    defaultValue = REGEX_DELETION_FILE
  )
  private Pattern searchDeleteFileMethod = CaseInsensitivePattern.compile(
    "^(" + REGEX_DELETION_FILE + ")"
  );

  @Override
  public void configure(Map<String, Object> configuration) {
    String searchDeleteFileMethodProperty =
      (String) configuration.getOrDefault("searchDeleteFileMethod", REGEX_DELETION_FILE);
    searchDeleteFileMethod = CaseInsensitivePattern.compile(
      "^(" + searchDeleteFileMethodProperty.replace(".", "\\.") + ")"
    );
  }

  /**
   * Ищем в коде ПолучитьИмяВременногоФайла и проверяем, есть ли удаление файла после использования.
   * Если удаление не найдено - фиксируется замечание.
   * Пример:
   * ИмяФайла = ПолучитьИмяВременногоФайла("mxl");
   * ТабличныйДокумент.Записать(ИмяФайла);
   * УдалитьФайлы(ИмяФайла);
   */
  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    var methodNameContext = ctx.methodName();
    if (methodNameContext == null) {
      return super.visitGlobalMethodCall(ctx);
    }

    var matcher = GET_TEMP_FILE_NAME_PATTERN.matcher(methodNameContext.getText());
    if (matcher.find()) {

      // просто получение имени временного файла без сохранения его в переменную
      // всегда ошибка
      String variableName = getVariableName(ctx);
      if (variableName == null) {
        diagnosticStorage.addDiagnostic(ctx);
        return super.visitGlobalMethodCall(ctx);
      }

      int filterLine = ctx.getStart().getLine();
      var codeBlockContext = (BSLParser.CodeBlockContext) Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_codeBlock);

      if (codeBlockContext != null
        && !foundDeleteFile(codeBlockContext, variableName, filterLine)) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    }

    return super.visitGlobalMethodCall(ctx);
  }

  private boolean foundDeleteFile(BSLParser.CodeBlockContext codeBlockContext, String variableName, int filterLine) {
    var result = false;

    var listCallStatements = Trees
      .findAllRuleNodes(codeBlockContext, BSLParser.RULE_globalMethodCall, BSLParser.RULE_accessCall)
      .stream()
      .map(BSLParserRuleContext.class::cast)
      .filter((BSLParserRuleContext node) -> node.getStart().getLine() > filterLine)
      .collect(Collectors.toList());

    for (var node : listCallStatements) {
      String fullCallMethod;
      BSLParser.DoCallContext doCallContext;

      if (node instanceof BSLParser.GlobalMethodCallContext) {
        fullCallMethod = ((BSLParser.GlobalMethodCallContext) node).methodName().getText();
        doCallContext = ((BSLParser.GlobalMethodCallContext) node).doCall();
      } else {
        fullCallMethod = getFullMethodName((BSLParser.AccessCallContext) node);
        doCallContext = ((BSLParser.AccessCallContext) node).methodCall().doCall();
      }

      if (doCallContext != null) {
        var matcher = searchDeleteFileMethod.matcher(fullCallMethod);
        if (matcher.matches() && fullCallMethod.length() > 0
          && foundVariableInCallParams(doCallContext, variableName)) {
          result = true;
          break;
        }
      }
    }
    return result;
  }

  private static boolean foundVariableInCallParams(BSLParser.DoCallContext doCallContext, String variableName) {

    var callParamListContext = doCallContext.callParamList();
    if (callParamListContext == null) {
      return false;
    }

    List<? extends BSLParser.CallParamContext> list = callParamListContext.callParam();
    if (list.isEmpty()) {
      return false;
    }

    var result = false;
    for (BSLParser.CallParamContext callParamContext : list) {
      if (callParamContext.getText().equalsIgnoreCase(variableName)) {
        result = true;
        break;
      }
    }

    return result;
  }

  private static String getVariableName(BSLParser.GlobalMethodCallContext ctx) {

    BSLParser.AssignmentContext assignment = (BSLParser.AssignmentContext)
      Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_assignment);

    if (assignment == null) {
      return null;
    }

    BSLParser.LValueContext lValue = assignment.lValue();
    if (lValue == null) {
      return null;
    }

    return lValue.getText();
  }

  private static String getFullMethodName(BSLParser.AccessCallContext ctx) {
    var parent = ctx.getParent();
    var prefix = "";
    List<? extends BSLParser.ModifierContext> modifiers;

    if (parent instanceof BSLParser.CallStatementContext) {

      var callStatement = (BSLParser.CallStatementContext) parent;

      modifiers = callStatement.modifier();
      if (callStatement.globalMethodCall() != null) {
        prefix = callStatement.globalMethodCall().methodName().IDENTIFIER().getText();
      } else {
        prefix = callStatement.IDENTIFIER().getText();
      }

    } else if (parent instanceof BSLParser.ModifierContext
      && parent.getParent() instanceof BSLParser.ComplexIdentifierContext) {

      var root = (BSLParser.ComplexIdentifierContext) parent.getParent();
      modifiers = root.modifier();

      var terminalNode = root.IDENTIFIER();
      if (terminalNode != null) {
        prefix = terminalNode.getText();
      }

    } else {
      // остальные к методам не относятся
      return "";
    }

    return prefix
      + modifiers.stream()
      .takeWhile(element -> element != parent)
      .map(ParseTree::getText)
      .collect(Collectors.joining())
      + "." + ctx.methodCall().methodName().IDENTIFIER().getText();
  }
}
