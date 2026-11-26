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


import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.Module;
import com.github._1c_syntax.bsl.mdo.ModuleOwner;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Range;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.SUSPICIOUS
  },
  modules = {
    ModuleType.SessionModule
  },
  scope = DiagnosticScope.BSL,
  canLocateOnProject = true
)

public class ProtectedModuleDiagnostic extends AbstractDiagnostic {

  /**
   * Рендж на который будут повешены замечания
   * Костыль, но пока так
   */
  private Range diagnosticRange;

  @Override
  protected void check() {

    var configuration = documentContext.getServerContext().getConfiguration();
    if (configuration.getConfigurationSource() == ConfigurationSource.EMPTY) {
      return;
    }

    diagnosticRange = documentContext.getSymbolTree().getModule().getSelectionRange();
    if (Ranges.isEmpty(diagnosticRange)) {
      return;
    }

    configuration.getChildren().stream()
      .filter(md -> md instanceof ModuleOwner)
      .map(md -> (ModuleOwner) md)
      .forEach((ModuleOwner moduleOwner) -> {
        var hasProtected = moduleOwner.getModules().stream()
          .filter(Module::isProtected)
          .findAny();
        if (hasProtected.isPresent()) {
          diagnosticStorage.addDiagnostic(diagnosticRange, info.getMessage(configuration.getMdoRefLocal(moduleOwner)));
        }
      });
  }
}
