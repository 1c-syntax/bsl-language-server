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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdo.support.UseMode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.STANDARD
  },
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_3
)
public class UsingModalWindowsDiagnostic extends AbstractVisitorDiagnostic {

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "false"
  )
  private boolean forceModalityMode;

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    var configuration = documentContext.getServerContext().getConfiguration().getMergedConfiguration();
    // если использование модальных окон разрешено (без предупреждения)
    // и не установлен флаг игнорирования использования модальных окон, то
    // ничего не диагностируется
    if (!forceModalityMode && configuration instanceof Configuration cf && cf.getModalityUseMode() == UseMode.USE) {
      return ctx;
    }

    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    String methodName = ctx.methodName().getText();
    SynchronousMethod.byName(methodName)
      .filter(SynchronousMethod::isModal)
      .ifPresent(method -> diagnosticStorage.addDiagnostic(ctx,
        info.getMessage(methodName, SynchronousMethod.analogOf(methodName))));
    return super.visitGlobalMethodCall(ctx);
  }
}
