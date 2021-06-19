/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.Configuration;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.SessionModule
  },
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.UNPREDICTABLE
  }
)
@RequiredArgsConstructor
public class OrdinaryAppSupportDiagnostic extends AbstractDiagnostic {

  private final LanguageServerConfiguration serverConfiguration;

  @Override
  protected void check() {

    if (!serverConfiguration.getDiagnosticsOptions().isOrdinaryAppSupport()) {
      return;
    }

    Ranges.getFirstSignificantTokenRange(documentContext.getTokens())
      .ifPresent(this::checkProperties);

  }

  private void checkProperties(Range range) {

    Configuration configuration = documentContext.getServerContext().getConfiguration();
    if (!configuration.isUseManagedFormInOrdinaryApplication()) {
      diagnosticStorage.addDiagnostic(range, info.getResourceString("managedFormInOrdinaryApp"));
    }

    if (configuration.isUseOrdinaryFormInManagedApplication()) {
      diagnosticStorage.addDiagnostic(range, info.getResourceString("ordinaryFormInManagedApp"));
    }

  }

}
