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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }
)
public class ReservedParameterNamesDiagnostic extends AbstractSymbolTreeDiagnostic {

  private static final String RESERVED_WORDS_DEFAULT = "";

  @DiagnosticParameter(type = String.class)
  private Pattern reservedWords = CaseInsensitivePattern.compile(RESERVED_WORDS_DEFAULT);

  @Override
  public void configure(Map<String, Object> configuration) {
    
    var incomingMask = (String) configuration.getOrDefault("reservedWords", RESERVED_WORDS_DEFAULT);

    this.reservedWords = CaseInsensitivePattern.compile("^" + incomingMask.trim() + "$");
  }

  @Override
  protected void check() {

    if (reservedWords.pattern().isBlank()) {
      return;
    }
    super.check();
  }

  @Override
  public void visitMethod(MethodSymbol methodSymbol) {

    List<ParameterDefinition> parameters = methodSymbol.getParameters();
    checkParameterName(parameters);
  }

  private void checkParameterName(List<ParameterDefinition> parameters) {

    parameters.forEach((ParameterDefinition parameter) -> {

      var matcher = reservedWords.matcher(parameter.getName());
      if (matcher.find()) {
        diagnosticStorage.addDiagnostic(parameter.getRange(), info.getMessage(parameter.getName()));
      }
    });
  }

}
