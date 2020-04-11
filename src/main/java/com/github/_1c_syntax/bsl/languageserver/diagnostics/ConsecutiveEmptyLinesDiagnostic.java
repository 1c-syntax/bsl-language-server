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
import com.github._1c_syntax.bsl.parser.BSLLexer;
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

  public ConsecutiveEmptyLinesDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {

    final var tokens = documentContext.getTokens();
    if (tokens.isEmpty()){
      return;
    }

    final int[] prevLineStorage = {0};
    tokens.stream()
      .map(Token::getLine)
      .distinct()
      .forEach(currLine -> {

        var prevLine = prevLineStorage[0];
        if (currLine > prevLine + 2) {
            addIssue(prevLine + 1);
        } else if (prevLine == 1 && currLine > 2) {
          // если как минимум первые две строки пустые
          addIssue(1);
        }
        prevLineStorage[0] = currLine;
      });

    checkLastToken(tokens, prevLineStorage[0]);
  }

  private void checkLastToken(List<Token> tokens, int prevLine) {
    // если в конце файла пустые строки, парсер может вернуть последний токен с тем же номером строки,
    // что и токен, где есть последнее использование идентификаторов. в тесте этот кейс проверяется.
    final var lastIndex = tokens.size() - 1;
    if (isOnlyWhiteSpacesLines(tokens.get(lastIndex))) {
      addIssue(prevLine + 1);
    }
  }

  private static boolean isOnlyWhiteSpacesLines(Token token) {
    return token.getChannel() == Token.HIDDEN_CHANNEL && token.getType() == BSLLexer.WHITE_SPACE
      && EMPTY_LINES_REGEX.matcher(token.getText()).matches();
  }

  private void addIssue(int startEmptyLine) {
    Range range = Ranges.create(startEmptyLine, 0, startEmptyLine + 1, 0);
    diagnosticStorage.addDiagnostic(range);
  }
}
