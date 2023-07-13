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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(
  type = DiagnosticType.SECURITY_HOTSPOT,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 15,
  scope = DiagnosticScope.BSL,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.STANDARD
  },
  modules = {
    ModuleType.CommonModule
  }
)
public class ExecuteExternalCodeInCommonModuleDiagnostic extends AbstractExecuteExternalCodeDiagnostic {

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    // если модуль не серверный, не внешнее соединение и не обычный клиент, то не проверяем
    if (documentContext.getMdObject()
      .filter(MDCommonModule.class::isInstance)
      .map(MDCommonModule.class::cast)
      .filter(commonModule -> commonModule.isServer()
        || commonModule.isClientOrdinaryApplication()
        || commonModule.isExternalConnection())
      .isEmpty()) {
      return ctx;
    }

    return super.visitFile(ctx);
  }
}
