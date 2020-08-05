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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  activatedByDefault = false,
  tags = {
    DiagnosticTag.BADPRACTICE
  }

)
public class TabAlignmentDiagnostic extends AbstractDiagnostic {

  private static final Pattern pattern = Pattern.compile("\\S[\\S ]*(\\t+)(?! *//)");

  @Override
  public void check() {

    String[] lines = documentContext.getContentList();
    for (int i = 0; i < lines.length; i++) {
      String currentLine = lines[i].strip();
      if (currentLine.startsWith("|")
        || currentLine.startsWith("//")) {
        continue;
      }

      Matcher matcher = pattern.matcher(lines[i].stripTrailing());
      if (matcher.find()) {
        diagnosticStorage.addDiagnostic(i, matcher.start(1), i, matcher.end(1));
      }
    }
  }
}
