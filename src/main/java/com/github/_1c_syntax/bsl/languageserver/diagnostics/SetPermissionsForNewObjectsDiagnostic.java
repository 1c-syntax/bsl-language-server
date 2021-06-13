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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import lombok.val;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.DESIGN
  },
  modules = {
    ModuleType.ManagedApplicationModule
  }
)
public class SetPermissionsForNewObjectsDiagnostic extends AbstractDiagnostic {

  private static final String NAME_FULL_ACCESS_ROLE_RU = "ПолныеПрава";
  private static final String NAME_FULL_ACCESS_ROLE_EN = "FullAccess";

  @Override
  public void check() {

    val configuration = documentContext.getServerContext().getConfiguration();

    val roles = configuration.getRoles();

    for (var role : roles)
    {
      var nameRole = role.getName();

      if (!nameRole.equals(NAME_FULL_ACCESS_ROLE_RU)
        && !nameRole.equals(NAME_FULL_ACCESS_ROLE_EN)
        && role.getRoleData().isSetForNewObjects())
      {

        var range = Ranges.getFirstSignificantTokenRange(documentContext.getTokens());
        if (range.isEmpty()) {
          return;
        }
        diagnosticStorage.addDiagnostic(range.get());
      }
    }

  }

}
