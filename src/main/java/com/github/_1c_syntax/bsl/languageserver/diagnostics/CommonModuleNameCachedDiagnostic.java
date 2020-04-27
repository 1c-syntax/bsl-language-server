/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.mdclasses.metadata.additional.ReturnValueReuse;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.CommonModule
  },
  minutesToFix = 5,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.UNPREDICTABLE
  }
)
public class CommonModuleNameCachedDiagnostic extends AbstractCommonModuleNameDiagnostic {

  private static final String REGEXP = "^.*повтисп|^.*сached";

  public CommonModuleNameCachedDiagnostic(DiagnosticInfo info) {
    super(info, REGEXP);
  }

  @Override
  protected boolean flagsCheck(CommonModule commonModule) {
    return commonModule.getReturnValuesReuse() == ReturnValueReuse.DURING_REQUEST
      || commonModule.getReturnValuesReuse() == ReturnValueReuse.DURING_SESSION;
  }

}
