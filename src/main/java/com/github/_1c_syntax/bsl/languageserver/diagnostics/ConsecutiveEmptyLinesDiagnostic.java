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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }

)
public class ConsecutiveEmptyLinesDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  private static final int DEFAULT_ALLOWED_EMPTY_LINES_COUNT = 1;
  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + DEFAULT_ALLOWED_EMPTY_LINES_COUNT
  )
  private int allowedEmptyLinesCount = DEFAULT_ALLOWED_EMPTY_LINES_COUNT;

  public ConsecutiveEmptyLinesDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {

    final var tokens = documentContext.getTokens();
    if (tokens.isEmpty()){
      return;
    }

    final int nonAllowedEmptyLinesCount = allowedEmptyLinesCount + 1;
    final int[] prevLineStorage = {0};
    tokens.stream()
      .filter(token -> !isWhiteSpace(token))
      .map(Token::getLine)
      .distinct()
      .forEachOrdered(currLine -> {

        var prevLine = prevLineStorage[0];
        if (currLine - prevLine > nonAllowedEmptyLinesCount) {
            addIssue(prevLine, currLine - 1);
        } else if (prevLine == 1 && currLine - 1 > nonAllowedEmptyLinesCount) {
          // если как минимум первые две строки пустые
          addIssue(0, currLine - 1);
        }
        prevLineStorage[0] = currLine;
      });

    checkLastToken(tokens, prevLineStorage[0]);
  }

  private void checkLastToken(List<Token> tokens, int prevLine) {
    // если в конце файла пустые строки, парсер может вернуть последний токен с тем же номером строки,
    // что и токен, где есть последнее использование идентификаторов. в тесте этот кейс проверяется.
    var eofLine = getEofToken(tokens).getLine();
    if (eofLine - prevLine > allowedEmptyLinesCount){
      addIssue(prevLine, eofLine);
    }
  }

  private static Token getEofToken(List<Token> tokens) {
    final var lastIndex = tokens.size() - 1;
    return tokens.get(lastIndex).getTokenSource().nextToken();
  }

  private static boolean isWhiteSpace(Token token) {
    return token.getChannel() == Token.HIDDEN_CHANNEL && token.getType() == BSLLexer.WHITE_SPACE;
  }

  private void addIssue(int startEmptyLine, int lastEmptyLine) {
    Range range = Ranges.create(startEmptyLine, 0, lastEmptyLine - 1, 0);
    diagnosticStorage.addDiagnostic(range);
  }

  @Override
  public List<CodeAction> getQuickFixes(
      List<Diagnostic> diagnostics, CodeActionParams params, DocumentContext documentContext) {

    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.forEach((Diagnostic diagnostic) -> {
      Range range = diagnostic.getRange();

      Range newRange = Ranges.create(range.getStart().getLine(), 0, range.getEnd().getLine() + 1, 0);
      // из-за того, что в DocumentContext.contentList пропускаются последние пустые строки в файле
      // приходится делать такую проверку
      try {
        documentContext.getText(newRange);
      } catch (ArrayIndexOutOfBoundsException e){
        return;
      }

      TextEdit textEdit = new TextEdit(newRange, "\n");
      textEdits.add(textEdit);

    });

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }
}
