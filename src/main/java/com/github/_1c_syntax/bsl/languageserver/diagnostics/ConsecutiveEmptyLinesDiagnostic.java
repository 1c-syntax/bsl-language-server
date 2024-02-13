/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
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

import java.util.List;
import java.util.stream.Collectors;

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

  @Override
  protected void check() {

    final var tokens = documentContext.getTokens();
    if (tokens.isEmpty()) {
      return;
    }

    final int[] prevLineStorage = {0};
    // без EOF, т.к. его проще и чуть быстрее обработать вне цикла
    tokens.subList(0, tokens.size() - 1)
      .stream()
      .filter(token -> token.getType() != BSLLexer.WHITE_SPACE)
      .mapToInt(Token::getLine)
      .distinct()
      .forEachOrdered((int currentLine) -> {
        checkEmptyLines(currentLine - 1, prevLineStorage[0]);
        prevLineStorage[0] = currentLine;
      });

    checkEmptyLines(getEofTokenLine(tokens), prevLineStorage[0]);
  }

  private void checkEmptyLines(int currentLine, int previousLine) {
    if (currentLine - previousLine > allowedEmptyLinesCount) {
      addIssue(previousLine, currentLine);
    }
  }

  private static int getEofTokenLine(List<Token> tokens) {
    return tokens.get(tokens.size() - 1).getLine();
  }

  private void addIssue(int startEmptyLine, int lastEmptyLine) {
    diagnosticStorage.addDiagnostic(startEmptyLine, 0, lastEmptyLine - 1, 0);
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics, CodeActionParams params, DocumentContext documentContext) {

    var eofTokenLine = getEofTokenLine(documentContext.getTokens());

    var textEdits = diagnostics.stream()
      .map(diagnostic -> getQuickFixText(diagnostic, eofTokenLine))
      .collect(Collectors.toList());

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }

  private static TextEdit getQuickFixText(Diagnostic diagnostic, int eofTokenLine) {
    Range range = diagnostic.getRange();

    int endLine = range.getEnd().getLine() + 1;
    String newText = "\n";
    if (endLine == eofTokenLine) {
      endLine--;
      newText = "";
    }
    Range newRange = Ranges.create(range.getStart().getLine(), 0, endLine, 0);
    return new TextEdit(newRange, newText);
  }
}
