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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }
)
public class IncorrectLineBreakDiagnostic extends AbstractDiagnostic {

  // check first symbol
  private static final boolean DEFAULT_CHECK_START = true;
  // forbidden characters at the beginning of the line
  private static final String DEFAULT_LIST_FOR_CHECK_START = "\\)|;|,|\\);";
  // check last symbol
  private static final boolean DEFAULT_CHECK_END = true;
  // forbidden end-of-line characters
  private static final String DEFAULT_LIST_FOR_CHECK_END = "ИЛИ|И|OR|AND|\\+|-|/|%|\\*";

  // +1 for next line and +1 for 1..n based line numbers.
  private static final int QUERY_START_LINE_OFFSET = 2;

  private final Set<Integer> queryFirstLines = new HashSet<>();

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_CHECK_START
  )
  private boolean checkFirstSymbol = DEFAULT_CHECK_START;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_LIST_FOR_CHECK_START
  )
  private Pattern listOfIncorrectFirstSymbol = createPatternIncorrectStartLine(DEFAULT_LIST_FOR_CHECK_START);

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_CHECK_END
  )
  private boolean checkLastSymbol = DEFAULT_CHECK_END;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_LIST_FOR_CHECK_END
  )
  private Pattern listOfIncorrectLastSymbol = createPatternIncorrectEndLine(DEFAULT_LIST_FOR_CHECK_END);

  @Override
  public void configure(Map<String, Object> configuration) {
    DiagnosticHelper.configureDiagnostic(this, configuration, "checkFirstSymbol", "checkLastSymbol");

    listOfIncorrectFirstSymbol = createPatternIncorrectStartLine(
      (String) configuration.getOrDefault("listOfIncorrectFirstSymbol", DEFAULT_LIST_FOR_CHECK_START)
    );
    listOfIncorrectLastSymbol = createPatternIncorrectEndLine(
      (String) configuration.getOrDefault("listOfIncorrectLastSymbol", DEFAULT_LIST_FOR_CHECK_END)
    );
  }

  @Override
  protected void check() {

    findQueryFirstLines();

    if (checkFirstSymbol){
      checkContent(listOfIncorrectFirstSymbol);
    }
    if (checkLastSymbol) {
      checkContent(listOfIncorrectLastSymbol);
    }
  }

  private void findQueryFirstLines() {
    documentContext.getQueries().forEach(query -> queryFirstLines.add(query.getAst().getStart().getLine()));
  }

  private void checkContent(Pattern pattern) {
    String[] contentList = documentContext.getContentList();
    String checkText;

    for (var i = 0; i < contentList.length; i++) {
      checkText = contentList[i];

      var matcher = pattern.matcher(checkText);

      if (matcher.find() && !queryFirstLines.contains(i + QUERY_START_LINE_OFFSET)) {
        diagnosticStorage.addDiagnostic(i, matcher.start(1), i, matcher.end(1));
      }
    }
  }

  private static Pattern getPatternSearch(String startPattern, String searchSymbols, String endPattern) {
    return CaseInsensitivePattern.compile(startPattern + searchSymbols + endPattern);
  }

  private static Pattern createPatternIncorrectStartLine(String listOfSymbols) {
    return getPatternSearch(
      "^\\s*(:?",
      listOfSymbols,
      ")"
    );
  }

  private static Pattern createPatternIncorrectEndLine(String listOfSymbols) {
    return getPatternSearch(
      "\\s+(:?",
      listOfSymbols,
      ")\\s*(?://.*)?$"
    );
  }
}
