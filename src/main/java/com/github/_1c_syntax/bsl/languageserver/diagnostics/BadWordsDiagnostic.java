/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  activatedByDefault = false,
  tags = {
    DiagnosticTag.DESIGN
  }
)
public class BadWordsDiagnostic extends AbstractDiagnostic {

  private static final String BAD_WORDS_DEFAULT = "";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = BAD_WORDS_DEFAULT
  )
  private Pattern badWords = CaseInsensitivePattern.compile(BAD_WORDS_DEFAULT);

  @Override
  public void configure(Map<String, Object> configuration) {
    this.badWords = CaseInsensitivePattern.compile(
      (String) configuration.getOrDefault("badWords", BAD_WORDS_DEFAULT));
  }

  @Override
  protected void check() {

    if (badWords.pattern().isBlank()) {
      return;
    }

    String[] moduleLines = documentContext.getContentList();
    for (int i = 0; i < moduleLines.length; i++) {
      Matcher matcher = badWords.matcher(moduleLines[i]);
      while (matcher.find()) {
        diagnosticStorage.addDiagnostic(i, matcher.start(), i, matcher.end());
      }
    }
  }
}
