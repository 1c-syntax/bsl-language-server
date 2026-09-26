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

import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.EnumSet;
import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.FormModule,
    ModuleType.CommandModule
  },
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class CompilationDirectiveNotAllowedDiagnostic extends AbstractVisitorDiagnostic {

  private static final Set<CompilerDirectiveKind> FORM_MODULE_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_CLIENT,
    CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_SERVER_NO_CONTEXT,
    CompilerDirectiveKind.AT_CLIENT_AT_SERVER_NO_CONTEXT
  );

  private static final Set<CompilerDirectiveKind> COMMAND_MODULE_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_CLIENT,
    CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_CLIENT_AT_SERVER
  );

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    if (documentContext.getModuleType() == ModuleType.FormModule) {
      var mdo = documentContext.getMdObject();
      if (mdo.isPresent() && mdo.get() instanceof Form form && form.getFormType() != FormType.MANAGED) {
        return ctx;
      }
    }
    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitCompilerDirective(BSLParser.CompilerDirectiveContext ctx) {
    var moduleType = documentContext.getModuleType();
    if (moduleType != ModuleType.FormModule && moduleType != ModuleType.CommandModule) {
      return ctx;
    }
    var allowedDirectives = moduleType == ModuleType.FormModule ? FORM_MODULE_DIRECTIVES : COMMAND_MODULE_DIRECTIVES;

    CompilerDirectiveKind.of(ctx.getStop().getType())
      .filter(kind -> !allowedDirectives.contains(kind))
      .ifPresent(kind -> diagnosticStorage.addDiagnostic(ctx, info.getMessage(ctx.getText())));
    return ctx;
  }
}
