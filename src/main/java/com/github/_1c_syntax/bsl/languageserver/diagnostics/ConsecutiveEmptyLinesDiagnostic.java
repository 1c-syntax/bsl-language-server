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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }

)
public class ConsecutiveEmptyLinesDiagnostic extends AbstractDiagnostic {
  private static final Pattern EMPTY_LINES_REGEX = Pattern.compile("^(\\s*[\\n\\r]+\\s*){2,}");
  private static final int WHITESPACE_TOKEN_TYPE = 2;

  public ConsecutiveEmptyLinesDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {

    List<Token> allTokens = documentContext.getTokens();

    if (allTokens.isEmpty()) {
      return;
    }
    int prevLine = -100;
    for (int i = 0; i < allTokens.size() - 1; i++) {
      Token token = allTokens.get(i);

      final var currLine = token.getLine();
      if (currLine == 1) {
        if (isOnlyWhiteSpacesLines(token)) {
          addIssue(1);
        }
      } else if (currLine > prevLine + 2) {
        addIssue(prevLine + 1);
      }
      prevLine = currLine;
    }

    //последнюю строку нужно проверять уже подробнее,
    // т.к. парсер, если в конце файла пустые строки, может вернуть последний токен с номером строки,
    // где есть последнее использование идентификаторов. в тесте этот кейс проверяется
    Token lastToken = allTokens.get(allTokens.size() - 1);
    if (isOnlyWhiteSpacesLines(lastToken)) {
      addIssue(lastToken.getLine() + 1);
    }

  }

  private static boolean isOnlyWhiteSpacesLines(Token token) {
    return token.getChannel() == Token.HIDDEN_CHANNEL && token.getType() == WHITESPACE_TOKEN_TYPE
      && EMPTY_LINES_REGEX.matcher(token.getText()).matches();
  }

  private void addIssue(int startEmptyLine) {
    Range range = Ranges.create(startEmptyLine, 0, startEmptyLine + 1, 0);
    diagnosticStorage.addDiagnostic(range);
  }
}
