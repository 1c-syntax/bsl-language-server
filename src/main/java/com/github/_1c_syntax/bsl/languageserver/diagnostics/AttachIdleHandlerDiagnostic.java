/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.RuleContext;

import java.util.Optional;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class AttachIdleHandlerDiagnostic extends AbstractFindMethodDiagnostic {
  private static final String BOOLEAN_TYPE = "Boolean";
  private static final String DATE_TYPE = "Datetime";
  private static final String NULL_TYPE = "Null";
  private static final String NUMBER_TYPE = "Number";
  private static final String STRING_TYPE = "String";
  private static final String UNDEFINED_TYPE = "Undefined";

  private static final Pattern MESSAGE_PATTERN = CaseInsensitivePattern.compile(
    "ПодключитьОбработчикОжидания|AttachIdleHandler|ОтключитьОбработчикОжидания|DetachIdleHandler"
  );

  /**
   * Конструктор по умолчанию
   *
   * @param info служебная информация о диагностике
   */
  AttachIdleHandlerDiagnostic(DiagnosticInfo info) {
    super(info, MESSAGE_PATTERN);
  }

  /**
   * Получение типа константного значения
   *
   * @param constValue - значение
   */
  private static String getTypeFromConstValue(BSLParser.ConstValueContext constValue) {
    String result;
    if (constValue.string() != null) {
      result = STRING_TYPE;
    } else if (constValue.DATETIME() != null) {
      result = DATE_TYPE;
    } else if (constValue.numeric() != null) {
      result = NUMBER_TYPE;
    } else if (constValue.TRUE() != null) {
      result = BOOLEAN_TYPE;
    } else if (constValue.FALSE() != null) {
      result = BOOLEAN_TYPE;
    } else if (constValue.NULL() != null) {
      result = NULL_TYPE;
    } else {
      result = UNDEFINED_TYPE;
    }

    return result;

  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (!getMethodPattern().matcher(ctx.methodName().getText()).matches()) {
      return false;
    }
    if (ctx.doCall().callParamList().callParam().size() == 0) {
      return false;
    }

    Optional<String> methodName = Optional.ofNullable(ctx.doCall())
      .map(BSLParser.DoCallContext::callParamList)
      .flatMap(callParamListContext -> callParamListContext.callParam().stream().findFirst())
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .flatMap(memberListContext -> memberListContext.stream().findFirst())
      .map(BSLParser.MemberContext::constValue)
      .filter(constValue -> getTypeFromConstValue(constValue).equals(STRING_TYPE))
      .map(RuleContext::getText)
      .map(constValueText -> constValueText.substring(1, constValueText.length() - 1));

    if (methodName.isPresent()) {
      boolean methodExist = documentContext.getSymbolTree().getMethods()
        .stream()
        .anyMatch(e -> e.getName().equalsIgnoreCase(methodName.get()));

      return !methodExist;
    }

    return false;
  }
}
