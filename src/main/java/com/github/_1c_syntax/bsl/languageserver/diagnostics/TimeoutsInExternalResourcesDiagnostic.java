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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.UNPREDICTABLE,
    DiagnosticTag.STANDARD
  }
)
public class TimeoutsInExternalResourcesDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern patternTimeout = Pattern.compile("^.(Таймаут|Timeout)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern patternNewExpression = Pattern.compile(
    "^(FTPСоединение|FTPConnection|HTTPСоединение|HTTPConnection|WSОпределения|WSDefinitions|" +
      "WSПрокси|WSProxy|ИнтернетПочтовыйПрофиль|InternetMailProfile)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final int DEFAULT_NUMBER_TIMEOUT = 5;

  public TimeoutsInExternalResourcesDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static String getVariableName(BSLParser.StatementContext statement) {
    String variableName = "";
    if (statement != null && statement.assignment() != null) {
      BSLParser.LValueContext lValueContext = statement.assignment().lValue();
      if (lValueContext != null) {
        variableName = lValueContext.getStart().getText();
      }
    }
    return variableName;
  }

  private static boolean isSpecificTypeName(BSLParser.NewExpressionContext newExpression) {
    BSLParser.TypeNameContext typeNameContext = newExpression.typeName();
    if (typeNameContext == null) {
      return false;
    }
    return patternNewExpression.matcher(typeNameContext.getText()).find();
  }

  private static boolean isWSDefinitions(BSLParser.NewExpressionContext newExpression) {
    return newExpression.typeName() != null && DiagnosticHelper.isWSDefinitionsType(newExpression.typeName());
  }

  private static boolean isFTPConnection(BSLParser.NewExpressionContext newExpression) {
    return newExpression.typeName() != null && DiagnosticHelper.isFTPConnectionType(newExpression.typeName());
  }

  private static boolean isNumberOrVariable(BSLParser.MemberContext member) {
    if (member.constValue() != null) {
      return (member.constValue().numeric() != null);
    } else {
      return (member.complexIdentifier() != null);
    }
  }

  private boolean checkTimeoutIntoParamList(BSLParser.NewExpressionContext newExpression, AtomicBoolean isContact) {
    BSLParser.DoCallContext doCallContext = newExpression.doCall();
    if (doCallContext == null) {
      return true;
    }

    int numberTimeout;
    if (isWSDefinitions(newExpression)) {
      numberTimeout = DEFAULT_NUMBER_TIMEOUT - 1; // 5-й
    }
    else if (isFTPConnection(newExpression)) {
      numberTimeout = DEFAULT_NUMBER_TIMEOUT + 1; // 7-ой
    }
    else {
      numberTimeout = DEFAULT_NUMBER_TIMEOUT; // 6-ой
    }

    List<BSLParser.CallParamContext> listParams = doCallContext.callParamList().callParam();
    if (listParams == null || listParams.size() <= numberTimeout) {
      return true;
    }

    boolean needContinue = true;
    BSLParser.ExpressionContext expression = listParams.get(numberTimeout).expression();
    if (expression != null && !expression.member().isEmpty()) {
      BSLParser.MemberContext memberContext = expression.member(0);
      if (isNumberOrVariable(memberContext)) {
        needContinue = false;
        isContact.set(false);
      }
    }

    return needContinue;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {
    Collection<ParseTree> list = Trees.findAllRuleNodes(ctx, BSLParser.RULE_newExpression);
    list.forEach((ParseTree e) -> {
      AtomicBoolean isContact = new AtomicBoolean(true);
      BSLParser.NewExpressionContext newExpression = (BSLParser.NewExpressionContext) e;
      if (isSpecificTypeName(newExpression)) {
        if (checkTimeoutIntoParamList(newExpression, isContact)) {
          BSLParser.StatementContext statementContext = (BSLParser.StatementContext)
            Trees.getAncestorByRuleIndex((ParserRuleContext) e, BSLParser.RULE_statement);
          String variableName = getVariableName(statementContext);
          int filterLine = newExpression.getStart().getLine();
          Collection<ParseTree> listNextStatements = Trees.findAllRuleNodes(ctx, BSLParser.RULE_statement)
            .stream()
            .filter(node -> ((BSLParser.StatementContext) node).getStart().getLine() > filterLine)
            .collect(Collectors.toList());
          checkNextStatement(listNextStatements, variableName, isContact);
        }
        if (isContact.get()) {
          diagnosticStorage.addDiagnostic(newExpression, info.getDiagnosticMessage());
        }
      }
    });
    return ctx;
  }

  private void checkNextStatement(
    Collection<ParseTree> listNextStatements,
    String variableName,
    AtomicBoolean isContact
  ) {
    listNextStatements.forEach((ParseTree element) -> {
      BSLParser.StatementContext localStatement = (BSLParser.StatementContext) element;
      String thisVariableName = getVariableName(localStatement);
      if (thisVariableName.equalsIgnoreCase(variableName)
        && isTimeoutModifer(localStatement)
        && isNumberOrVariable(localStatement.assignment().expression().member(0))) {

        isContact.set(false);

      }
    });
  }

  private boolean isTimeoutModifer(BSLParser.StatementContext localStatement) {

    BSLParser.AssignmentContext assignmentContext = localStatement.assignment();
    if (assignmentContext == null) {
      return false;
    }

    BSLParser.LValueContext lValue = assignmentContext.lValue();
    if (!lValue.isEmpty()) {

      BSLParser.AcceptorContext acceptor = lValue.acceptor();
      if (acceptor != null) {

        List<ParseTree> allRuleNodes = new ArrayList<>(Trees.findAllRuleNodes(acceptor, BSLParser.RULE_accessProperty));
        if (!allRuleNodes.isEmpty()) {

          BSLParser.AccessPropertyContext accessProperty = (BSLParser.AccessPropertyContext) allRuleNodes.get(0);
          return patternTimeout.matcher(accessProperty.getText()).find();

        }
      }
    }

    return false;
  }
}
