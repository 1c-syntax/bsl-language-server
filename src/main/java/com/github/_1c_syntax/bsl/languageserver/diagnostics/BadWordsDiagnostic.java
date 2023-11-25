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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private static final boolean FIND_IN_COMMENTS = true;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = BAD_WORDS_DEFAULT
  )
  private Pattern badWords = CaseInsensitivePattern.compile(BAD_WORDS_DEFAULT);

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + FIND_IN_COMMENTS
  )
  private boolean findInComments = FIND_IN_COMMENTS;

  @Override
  public void configure(Map<String, Object> configuration) {
    this.badWords = CaseInsensitivePattern.compile(
      (String) configuration.getOrDefault("badWords", BAD_WORDS_DEFAULT));
    this.findInComments = (boolean) configuration.getOrDefault("findInComments", FIND_IN_COMMENTS);
  }

  @Override
  protected void check() {

    if (badWords.pattern().isBlank()) {
      return;
    }

    var moduleLines = getContentList();
    for (var i = 0; i < moduleLines.length; i++) {
      final var moduleLine = moduleLines[i];
      if (moduleLine.isEmpty()) {
        continue;
      }
      var matcher = badWords.matcher(moduleLine);
      while (matcher.find()) {
        diagnosticStorage.addDiagnostic(i, matcher.start(), i, matcher.end());
      }
    }
  }

  private String[] getContentList() {
    final var moduleLines = documentContext.getContentList();
    if (findInComments) {
      return moduleLines;
    }
    final var lineNumbersWithoutComments = getLineNumbersWithoutComments();
    if (lineNumbersWithoutComments.isEmpty()) {
      return moduleLines;
    }
    final List<String> result = new ArrayList<>(lineNumbersWithoutComments.size());
    for (var i = 0; i < moduleLines.length; i++) {
      if (lineNumbersWithoutComments.contains(i)) {
        result.add(moduleLines[i]);
      } else {
        result.add("");
      }
    }
    return result.toArray(new String[0]);
  }

  private Set<Integer> getLineNumbersWithoutComments() {
    var result = new HashSet<Integer>();
    final var tokens = documentContext.getTokensFromDefaultChannel();
    int lastLine = -1;
    for (var token : tokens) {
      var line = token.getLine();
      if (line > lastLine) {
        lastLine = line;
        result.add(line - 1); // т.к. в токенах нумерация строк с 1
      }
    }
    return result;
  }
}
