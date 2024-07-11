/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.UNUSED
  },
  modules = {
    ModuleType.CommandModule,
    ModuleType.CommonModule,
    ModuleType.ManagerModule,
    ModuleType.ValueManagerModule,
    ModuleType.SessionModule,
    ModuleType.UNKNOWN
  }
)
@RequiredArgsConstructor
public class UnusedLocalVariableDiagnostic extends AbstractDiagnostic {
  private final ReferenceIndex referenceIndex;
  private static final Set<VariableKind> CHECKING_VARIABLE_KINDS = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.LOCAL,
    VariableKind.DYNAMIC
  );

  @Override
  public void check() {
    documentContext.getSymbolTree().getVariables().stream()
      .filter(variable -> CHECKING_VARIABLE_KINDS.contains(variable.getKind()))
      .filter(variable -> !variable.isExport())
      .filter(variable -> referenceIndex.getReferencesTo(variable).stream()
        .filter(ref -> ref.getOccurrenceType() == OccurrenceType.REFERENCE).findFirst().isEmpty()
      )
      .forEach(variable -> diagnosticStorage.addDiagnostic(
        variable.getSelectionRange(), info.getMessage(variable.getName()))
      );
  }
}
