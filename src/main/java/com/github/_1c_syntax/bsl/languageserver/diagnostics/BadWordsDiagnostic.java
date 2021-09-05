/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.Range;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
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
  private Pattern badWords = createPattern(BAD_WORDS_DEFAULT);

  private static Pattern createPattern(String words) {
    return CaseInsensitivePattern.compile(words);
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    this.badWords = createPattern(
      (String) configuration.getOrDefault("badWords", BAD_WORDS_DEFAULT));
  }

  @Override
  protected void check() {

    if (badWords.pattern().trim().length() == 0) {
      return;
    }

    String moduleText = String.join("\n", documentContext.getContentList());
    Matcher matcher = badWords.matcher(moduleText);

    while (matcher.find()) {
      diagnosticStorage.addDiagnostic(rangeOfError(moduleText, matcher));
    }
  }

  private Range rangeOfError(String moduleText, Matcher matcher) {

    String textBefore = moduleText.substring(0, matcher.start());

    int lineNum = StringUtils.countMatches(textBefore, "\n");
    int shift = textBefore.lastIndexOf("\n") + 1;

    return Ranges.create(lineNum, matcher.start() - shift, matcher.end() - shift);
  }
}
