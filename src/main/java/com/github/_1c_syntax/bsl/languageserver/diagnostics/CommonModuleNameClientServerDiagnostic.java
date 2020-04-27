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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.CommonModule
  },
  minutesToFix = 2,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.STANDARD,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class CommonModuleNameClientServerDiagnostic extends AbstractDiagnostic {

  private static final Pattern pattern = Pattern.compile(
    "^.*клиентсервер|^.*clientserver",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public CommonModuleNameClientServerDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {

    if (documentContext.getTokens().isEmpty()) {
      return;
    }

    documentContext.getMdObject()
      .map(CommonModule.class::cast)
      .filter(CommonModule::isServer)
      .filter(CommonModule::isClientManagedApplication)
      .filter(commonModule -> !pattern.matcher(commonModule.getName()).matches())
      .ifPresent(commonModule -> diagnosticStorage.addDiagnostic(documentContext.getTokens().get(0)));

  }

}
