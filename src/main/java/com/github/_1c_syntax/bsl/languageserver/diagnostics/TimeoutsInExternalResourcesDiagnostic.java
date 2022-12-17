/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import edu.umd.cs.findbugs.annotations.Nullable;
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

  private static final Pattern PATTERN_TIMEOUT = CaseInsensitivePattern.compile(
    "^\\.(Таймаут|Timeout)"
  );
  private static final Pattern PATTERN_NEW_EXPRESSION = CaseInsensitivePattern.compile(
    "^(FTPСоединение|FTPConnection|HTTPСоединение|HTTPConnection|WSОпределения|WSDefinitions|WSПрокси|WSProxy)"
  );
  private static final Pattern PATTERN_NEW_EXPRESSION_WITH_MAIL = CaseInsensitivePattern.compile(
    "^(FTPСоединение|FTPConnection|HTTPСоединение|HTTPConnection|WSОпределения|WSDefinitions|WSПрокси|WSProxy" +
      "|ИнтернетПочтовыйПрофиль|InternetMailProfile)"
  );

  private static final boolean ANALYZING_MAIL = true;

  private int defaultNumberTimeout = 5;
  private int defaultNumberTimeoutFtp = 6;
  private int defaultNumberTimeoutWsd = 4;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + ANALYZING_MAIL
  )
  private boolean analyzeInternetMailProfileZeroTimeout = ANALYZING_MAIL;

  private Pattern getPatternNewExpression() {
    if (analyzeInternetMailProfileZeroTimeout) {
      return PATTERN_NEW_EXPRESSION_WITH_MAIL;
    } else {
      return PATTERN_NEW_EXPRESSION;
    }
  }

  private static String getVariableName(@Nullable BSLParser.StatementContext statement) {
    var variableName = "";
    if (statement != null && statement.assignment() != null) {
      var lValueContext = statement.assignment().lValue();
      if (lValueContext != null) {
        variableName = lValueContext.getStart().getText();
      }
    }
    return variableName;
  }

  private boolean isSpecificTypeName(BSLParser.NewExpressionContext newExpression) {
    var typeNameContext = newExpression.typeName();
    if (typeNameContext == null) {
      return false;
    }
    return getPatternNewExpression().matcher(typeNameContext.getText()).find();
  }

  private static boolean isWSDefinitions(BSLParser.NewExpressionContext newExpression) {
    return newExpression.typeName() != null && DiagnosticHelper.isWSDefinitionsType(newExpression.typeName());
  }

  private static boolean isFTPConnection(BSLParser.NewExpressionContext newExpression) {
    return newExpression.typeName() != null && DiagnosticHelper.isFTPConnectionType(newExpression.typeName());
  }

  private static boolean isInternetMailProfile(BSLParser.NewExpressionContext newExpression) {
    return newExpression.typeName() != null && DiagnosticHelper.isInternetMailProfileType(newExpression.typeName());
  }

  private static boolean isNumberOrVariable(BSLParser.MemberContext member) {
    if (member.constValue() != null) {
      return (member.constValue().numeric() != null);
    } else {
      return (member.complexIdentifier() != null);
    }
  }

  private boolean checkTimeoutIntoParamList(BSLParser.NewExpressionContext newExpression, AtomicBoolean isContact) {
    var doCallContext = newExpression.doCall();
    if (doCallContext == null) {
      return true;
    }

    int numberTimeout;
    if (isWSDefinitions(newExpression)) {
      numberTimeout = defaultNumberTimeoutWsd;
    } else if (isFTPConnection(newExpression)) {
      numberTimeout = defaultNumberTimeoutFtp;
    } else if (isInternetMailProfile(newExpression)) {
      numberTimeout = 5;
    } else {
      numberTimeout = defaultNumberTimeout;
    }

    List<? extends BSLParser.CallParamContext> listParams = doCallContext.callParamList().callParam();
    if (listParams == null || listParams.size() <= numberTimeout) {
      return true;
    }

    var needContinue = true;
    BSLParser.ExpressionContext expression = listParams.get(numberTimeout).expression();
    if (expression != null && !expression.member().isEmpty()) {
      var memberContext = expression.member(0);
      if (isNumberOrVariable(memberContext)) {
        needContinue = false;
        isContact.set(false);
      }
    }

    return needContinue;
  }

  private static void checkNextStatement(
    Collection<BSLParser.StatementContext> listNextStatements,
    String variableName,
    AtomicBoolean isContact
  ) {
    listNextStatements.forEach((BSLParser.StatementContext localStatement) -> {
      String thisVariableName = getVariableName(localStatement);
      if (thisVariableName.equalsIgnoreCase(variableName)
        && isTimeoutModifier(localStatement)
        && isNumberOrVariable(localStatement.assignment().expression().member(0))) {

        isContact.set(false);

      }
    });
  }

  private static boolean isTimeoutModifier(BSLParser.StatementContext localStatement) {

    var assignmentContext = localStatement.assignment();
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
          return PATTERN_TIMEOUT.matcher(accessProperty.getText()).find();

        }
      }
    }

    return false;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {
    Trees.findAllRuleNodes(ctx, BSLParser.RULE_newExpression).stream()
      .map(BSLParser.NewExpressionContext.class::cast)
      .forEach((BSLParser.NewExpressionContext newExpression) -> {
        var isContact = new AtomicBoolean(true);
        if (isSpecificTypeName(newExpression)) {
          if (checkTimeoutIntoParamList(newExpression, isContact)) {
            var statementContext = (BSLParser.StatementContext)
              Trees.getAncestorByRuleIndex(newExpression, BSLParser.RULE_statement);
            String variableName = getVariableName(statementContext);
            int filterLine = newExpression.getStart().getLine();
            Collection<BSLParser.StatementContext> listNextStatements =
              Trees.findAllRuleNodes(ctx, BSLParser.RULE_statement)
                .stream()
                .map(node -> (BSLParser.StatementContext) node)
                .filter(node -> node.getStart().getLine() > filterLine)
                .collect(Collectors.toList());
            checkNextStatement(listNextStatements, variableName, isContact);
          }
          if (isContact.get()) {
            diagnosticStorage.addDiagnostic(newExpression, info.getMessage());
          }
        }
      });
    return ctx;
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    var diagnosticCompatibility = documentContext
      .getServerContext()
      .getConfiguration()
      .getCompatibilityMode();

    if (diagnosticCompatibility != null
      && CompatibilityMode.compareTo(diagnosticCompatibility,
      DiagnosticCompatibilityMode.UNDEFINED.getCompatibilityMode()) != 0) {

      if (CompatibilityMode.compareTo(diagnosticCompatibility,
        DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_7.getCompatibilityMode()) == 1) {
        defaultNumberTimeout = 4;
        defaultNumberTimeoutWsd = 3;
      }
      if (CompatibilityMode.compareTo(diagnosticCompatibility,
        DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_9.getCompatibilityMode()) == 1) {
        defaultNumberTimeoutFtp = 5;
      }
    }

    return super.visitFile(ctx);
  }
}
