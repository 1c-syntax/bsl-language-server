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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_21,
  tags = {
    DiagnosticTag.DEPRECATED,
    DiagnosticTag.PERFORMANCE
  }
)
public class DeprecatedHttpConnectionMethodDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern MESSAGE_PATTERN = CaseInsensitivePattern.compile(
    "(ВызватьHTTPМетод|CallHTTPMethod|"
    + "Записать|Write|"
    + "Изменить|Change|Modify|"
    + "ОтправитьДляОбработки|SendForProcessing|"
    + "Получить|Get|"
    + "ПолучитьЗаголовки|GetHeaders|"
    + "Удалить|Delete)"
  );

  private static final Pattern HTTP_CONNECTION_PATTERN = CaseInsensitivePattern.compile(
    "HTTPСоединение|HTTPConnection"
  );

  private final TypeService typeService;

  public DeprecatedHttpConnectionMethodDiagnostic(TypeService typeService) {
    this.typeService = typeService;
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    var methodName = ctx.methodName();
    if (methodName == null) {
      return ctx;
    }

    if (!MESSAGE_PATTERN.matcher(methodName.getText()).matches()) {
      return ctx;
    }

    var identifier = methodName.IDENTIFIER();
    if (identifier == null) {
      return ctx;
    }

    var typedMember = typeService.memberAt(documentContext, identifier);
    if (typedMember.isEmpty()) {
      return ctx;
    }

    var owner = typedMember.get().owner();
    if (owner != null && HTTP_CONNECTION_PATTERN.matcher(owner.qualifiedName()).matches()) {
      diagnosticStorage.addDiagnostic(methodName, info.getMessage(methodName.getText()));
    }

    return ctx;
  }

}
