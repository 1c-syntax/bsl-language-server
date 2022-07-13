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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDOForm;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR,
    DiagnosticTag.UNPREDICTABLE,
    DiagnosticTag.SUSPICIOUS
  },
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.FormModule
  }
)
public class ServerSideExportFormMethodDiagnostic extends AbstractSymbolTreeDiagnostic {

  @Override
  public void visitModule(ModuleSymbol module) {
    documentContext.getMdObject().ifPresent((AbstractMDObjectBase mdo) -> {
      // проверка актуальна только для управляемых форм
      if (mdo instanceof AbstractMDOForm && ((AbstractMDOForm) mdo).getFormType() != FormType.ORDINARY) {
        super.visitModule(module);
      }
    });
  }

  @Override
  public void visitMethod(MethodSymbol method) {
    if (method.isExport()
      && method.getCompilerDirectiveKind()
      .orElse(CompilerDirectiveKind.AT_SERVER) != CompilerDirectiveKind.AT_CLIENT) {
      diagnosticStorage.addDiagnostic(method);
    }
  }
}
