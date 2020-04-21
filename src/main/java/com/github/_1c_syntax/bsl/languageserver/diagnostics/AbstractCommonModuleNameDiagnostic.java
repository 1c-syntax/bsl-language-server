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
import com.github._1c_syntax.mdclasses.mdo.CommonModule;

import java.util.regex.Pattern;

abstract class AbstractCommonModuleNameDiagnostic extends AbstractDiagnostic {

  private final Pattern pattern;

  public AbstractCommonModuleNameDiagnostic(DiagnosticInfo info, String regexp) {
    super(info);

    pattern = Pattern.compile(
      regexp,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

  }

  @Override
  protected void check(DocumentContext documentContext) {
    if (documentContext.getTokens().isEmpty()) {
      return;
    }

    documentContext.getMdObject()
      .filter(CommonModule.class::isInstance)
      .map(CommonModule.class::cast)
      .filter(this::flagsCheck)
      .filter(commonModule -> !pattern.matcher(commonModule.getName()).find())
      .ifPresent(commonModule -> diagnosticStorage.addDiagnostic(documentContext.getTokens().get(0)));
  }

  protected abstract boolean flagsCheck(CommonModule commonModule);

}
