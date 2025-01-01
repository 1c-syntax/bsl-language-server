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
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.Register;
import com.github._1c_syntax.bsl.mdo.children.Dimension;
import com.github._1c_syntax.bsl.types.MDOType;

import java.util.List;

@DiagnosticMetadata(
  activatedByDefault = false,
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  },
  scope = DiagnosticScope.BSL,
  canLocateOnProject = true
)
public class DenyIncompleteValuesDiagnostic extends AbstractMetadataDiagnostic {

  public DenyIncompleteValuesDiagnostic() {
    super(List.of(
      MDOType.INFORMATION_REGISTER,
      MDOType.ACCUMULATION_REGISTER,
      MDOType.ACCOUNTING_REGISTER,
      MDOType.CALCULATION_REGISTER
    ));
  }

  @Override
  protected void checkMetadata(MD mdo) {
    if (mdo instanceof Register register) {
      register.getDimensions().stream()
        .filter(dimension -> !dimension.isDenyIncompleteValues())
        .forEach((Dimension dimension) -> {
          var ownerMDOName = MdoRefBuilder.getLocaleOwnerMdoName(documentContext, mdo);
          addDiagnostic(info.getMessage(dimension.getName(), ownerMDOName));
        });
    }
  }
}
