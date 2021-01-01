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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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

  public static final String REGEX_DELETION_FILE = "УдалитьФайлы|DeleteFiles|НачатьУдалениеФайлов|BeginDeletingFiles|ПереместитьФайл|MoveFile";

  private static final Pattern searchGetTempFileName = CaseInsensitivePattern.compile(
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
      "^(" + searchDeleteFileMethodProperty + ")"
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

    BSLParser.MethodNameContext methodNameContext = ctx.methodName();
    if (methodNameContext == null) {
      return super.visitGlobalMethodCall(ctx);
    }

    Matcher matcher = searchGetTempFileName.matcher(methodNameContext.getText());
    if (matcher.find()) {

      String variableName = getVariableName(ctx);
      if (variableName == null) {
        diagnosticStorage.addDiagnostic(ctx);
        return super.visitGlobalMethodCall(ctx);
      }

      int filterLine = ctx.getStart().getLine();
      BSLParser.CodeBlockContext codeBlockContext = (BSLParser.CodeBlockContext)
        Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_codeBlock);

      if (codeBlockContext != null
        && !foundDeleteFile(codeBlockContext, variableName, filterLine)) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    }

    return super.visitGlobalMethodCall(ctx);
  }

  private boolean foundDeleteFile(BSLParser.CodeBlockContext codeBlockContext, String variableName, int filterLine) {
    boolean result = false;

    Collection<ParseTree> listCallStatements = Trees
      .findAllRuleNodes(codeBlockContext, BSLParser.RULE_callStatement)
      .stream()
      .filter(node -> ((BSLParser.CallStatementContext) node).getStart().getLine() > filterLine)
      .collect(Collectors.toList());

    for (ParseTree node : listCallStatements) {
      BSLParser.CallStatementContext localCallStatementContext = ((BSLParser.CallStatementContext) node);

      BSLParser.GlobalMethodCallContext localGlobalMethodCall = localCallStatementContext.globalMethodCall();
      // получаем full call method и полное имя вызова
      String fullCallMethod;
      BSLParser.DoCallContext doCallContext;
      if (localGlobalMethodCall == null) {
        fullCallMethod = getFullCallMethod(localCallStatementContext);
        doCallContext = getDoCallFromCallStatement(localCallStatementContext);
      } else {
        fullCallMethod = localGlobalMethodCall.methodName().getText();
        doCallContext = localGlobalMethodCall.doCall();
      }
      if (doCallContext != null) {
        Matcher matcher = searchDeleteFileMethod.matcher(fullCallMethod);
        if (matcher.find() && fullCallMethod.length() > 0 && foundVariableInCallParams(doCallContext, variableName)) {
          result = true;
          break;
        }
      }
    }
    return result;
  }

  private static BSLParser.DoCallContext getDoCallFromCallStatement(
    BSLParser.CallStatementContext callStatementContext
  ) {
    BSLParser.MethodCallContext methodCallContext = callStatementContext.accessCall().methodCall();
    if (methodCallContext == null) {
      return null;
    }
    return methodCallContext.doCall();
  }

  private static boolean foundVariableInCallParams(BSLParser.DoCallContext doCallContext, String variableName) {

    BSLParser.CallParamListContext callParamListContext = doCallContext.callParamList();
    if (callParamListContext == null) {
      return false;
    }

    List<? extends BSLParser.CallParamContext> list = callParamListContext.callParam();
    if (list.isEmpty()) {
      return false;
    }

    boolean result = false;
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

  // TODO: перенести в TREES или в BSL parser
  private static String getFullCallMethod(BSLParser.CallStatementContext ctx) {
    StringBuilder builder = new StringBuilder();
    builder.append(ctx.getStart().getText());
    BSLParser.AccessCallContext accessCallContext = ctx.accessCall();
    if (accessCallContext != null) {
      for (ParseTree node : accessCallContext.children) {
        if (node instanceof TerminalNodeImpl) {
          builder.append(node.getText());
        } else if (node instanceof BSLParser.MethodCallContext) {
          builder.append(((BSLParser.MethodCallContext) node).methodName().getText());
        }
      }
    }
    return builder.toString();
  }

}
