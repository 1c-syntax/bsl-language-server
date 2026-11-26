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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.Map;
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
  private static final boolean FIND_IN_COMMENTS_DEFAULT = true;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = BAD_WORDS_DEFAULT
  )
  private Pattern badWords = CaseInsensitivePattern.compile(BAD_WORDS_DEFAULT);

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + FIND_IN_COMMENTS_DEFAULT
  )
  private boolean findInComments = FIND_IN_COMMENTS_DEFAULT;

  @Override
  public void configure(Map<String, Object> configuration) {
    this.badWords = CaseInsensitivePattern.compile(
      (String) configuration.getOrDefault("badWords", BAD_WORDS_DEFAULT));
    this.findInComments = (boolean) configuration.getOrDefault("findInComments", FIND_IN_COMMENTS_DEFAULT);
  }

  @Override
  protected void check() {

    if (badWords.pattern().isBlank()) {
      return;
    }
    var moduleLines = documentContext.getContentList();
    if (findInComments) {
      checkAllLines(moduleLines);
      return;
    }
    checkLinesWithoutComments(moduleLines);
  }

  private void checkAllLines(String[] moduleLines) {
    for (var i = 0; i < moduleLines.length; i++) {
      checkLine(moduleLines, i);
    }
  }

  private void checkLinesWithoutComments(String[] moduleLines) {
    final var nclocData = documentContext.getMetrics().getNclocData();
    for (int i : nclocData) {
      final var moduleNumber = i - 1; // т.к. в токенах нумерация строк с 1, а в moduleLines с 0
      checkLine(moduleLines, moduleNumber);
    }
  }

  private void checkLine(String[] lines, int lineNumber) {
    var moduleLine = lines[lineNumber];
    if (moduleLine.isEmpty()) {
      return;
    }
    var matcher = badWords.matcher(moduleLine);
    while (matcher.find()) {
      diagnosticStorage.addDiagnostic(
        Ranges.create(lineNumber, matcher.start(), lineNumber, matcher.end()),
        info.getMessage(matcher.group())
      );
    }
  }
}
