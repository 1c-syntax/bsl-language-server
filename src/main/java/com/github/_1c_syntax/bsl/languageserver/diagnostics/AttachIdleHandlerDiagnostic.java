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
import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8TypeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

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
public class AttachIdleHandlerDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern MESSAGE_PATTERN_ATTACH = CaseInsensitivePattern.compile(
    "ПодключитьОбработчикОжидания|AttachIdleHandler"
  );
  private static final Pattern MESSAGE_PATTERN_DETACH = CaseInsensitivePattern.compile(
    "ОтключитьОбработчикОжидания|DetachIdleHandler"
  );

  public AttachIdleHandlerDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (checkGlobalMethodCall(ctx)) {
      diagnosticStorage.addDiagnostic(ctx.methodName(), getMessage(ctx));
    }

    return super.visitGlobalMethodCall(ctx);
  }

  /**
   * Получает сообщение диагностики для пользователя
   *
   * @param ctx контекст узла
   * @return В случае если передан контекст метода, параметризованное сообщение,
   * первым параметром которого <b>всегда</b> будет имя метода.
   * В противном случае возвращается обычное сообщение без параметров.
   */
  protected String getMessage(BSLParser.GlobalMethodCallContext ctx) {
    return ctx.methodName().getText();
  }

  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    boolean isAttachHandler = MESSAGE_PATTERN_ATTACH.matcher(ctx.methodName().getText()).matches();
    if (!(isAttachHandler
      || MESSAGE_PATTERN_DETACH.matcher(ctx.methodName().getText()).matches())) {
      return false;
    }

    var callContext = ctx.doCall();
    // Проверка на существование метода в текущем контексте без параметров
    boolean hasError = V8TypeHelper.getStringConstantFromFirstParam(callContext)
      .get().map(methodName -> documentContext.getSymbolTree().getMethods()
        .stream()
        .noneMatch(method -> method.getName().equalsIgnoreCase(methodName) && method.getParameters().size() == 0)
      ).orElse(false);

    if (!isAttachHandler) {
      return hasError;
    }

    // Проверка что при таймауте меньше 1 секунды, третий параметр не равен Ложь
    hasError = hasError || V8TypeHelper.getFloatNumberConstantFromParam(callContext, 1)
      .get()
      .filter(timeout -> timeout < 1.0f)
      // TODO change while got context
      .map(e -> V8TypeHelper.getBooleanConstantFromParam(callContext, 2, Boolean.FALSE)
        // .map(e -> V8TypeHelper.get(Boolean.FALSE, (constValue) -> constValue.TRUE() != null).apply(callContext,2)
        .get()
        .map(be -> be.equals(Boolean.FALSE))
        .orElse(false)
      )
      .orElse(false);
    return hasError;
  }
}

