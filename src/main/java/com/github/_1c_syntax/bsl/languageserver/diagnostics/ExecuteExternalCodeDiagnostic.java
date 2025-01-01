/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 1,
  scope = DiagnosticScope.BSL,
  tags = {
    DiagnosticTag.ERROR,
    DiagnosticTag.STANDARD
  },
  modules = {
    ModuleType.CommandModule,
    ModuleType.ExternalConnectionModule,
    ModuleType.FormModule,
    ModuleType.HTTPServiceModule,
    ModuleType.ObjectModule,
    ModuleType.OrdinaryApplicationModule,
    ModuleType.RecordSetModule,
    ModuleType.ValueManagerModule,
    ModuleType.WEBServiceModule,
    ModuleType.SessionModule
  }
)
public class ExecuteExternalCodeDiagnostic extends AbstractExecuteExternalCodeDiagnostic {

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {

    // если только клиентская аннотация, тогда ничего не делаем
    if (ctx.funcDeclaration().compilerDirective().stream()
      .anyMatch(compilerDirectiveContext
        -> compilerDirectiveContext.getStop().getType() == BSLParser.ANNOTATION_ATCLIENT_SYMBOL)) {
      return ctx;
    }

    return super.visitFunction(ctx);
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {

    // если только клиентская аннотация, тогда ничего не делаем
    if (ctx.procDeclaration().compilerDirective().stream()
      .anyMatch(compilerDirectiveContext
        -> compilerDirectiveContext.getStop().getType() == BSLParser.ANNOTATION_ATCLIENT_SYMBOL)) {
      return ctx;
    }

    return super.visitProcedure(ctx);
  }
}
