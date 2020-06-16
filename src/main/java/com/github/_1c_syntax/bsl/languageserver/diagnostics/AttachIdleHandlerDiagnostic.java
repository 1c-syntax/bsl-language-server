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
import com.github._1c_syntax.bsl.languageserver.utils.V8TypeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  modules = {
    ModuleType.FormModule
  },
  tags = {
    DiagnosticTag.ERROR,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class AttachIdleHandlerDiagnostic extends AbstractFindMethodDiagnostic {

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

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (!getMethodPattern().matcher(ctx.methodName().getText()).matches()) {
      return false;
    }
    var callContext = ctx.doCall();
    // Проверка на существование метода в текущем контексте без параметров
    boolean hasError = V8TypeHelper.getStringConstantFromFirstParam(callContext)
      .get().map(methodName -> {
        boolean methodExist = documentContext.getSymbolTree().getMethods()
          .stream()
          .anyMatch(e -> e.getName().equalsIgnoreCase(methodName)
            && e.getParameters().size() == 0);
        return !methodExist;
      }).orElse(false);

    // Проверка что при таймауте меньше 1 секунды, третий параметр не равен Ложь
    hasError = hasError || V8TypeHelper.getFloatNumberConstantFromParam(callContext, 1)
      .get()
      .filter(e -> e < 1.0)
      .map(e -> V8TypeHelper.getBooleanConstantFromParam(callContext, 2)
        .get().map(be -> be.equals(Boolean.FALSE))
        .orElse(false)
      )
      .orElse(false);
    return hasError;
  }
}

