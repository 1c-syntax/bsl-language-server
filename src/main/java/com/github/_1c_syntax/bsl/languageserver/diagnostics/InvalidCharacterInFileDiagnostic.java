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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR,
    DiagnosticTag.STANDARD,
    DiagnosticTag.UNPREDICTABLE
  }
)

public class InvalidCharacterInFileDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  public static final String SPACE_REGEX = "(?:" +
    "\\u00A0" + // 160
    ")";

  private static final Pattern ILLEGAL_PATTERN = Pattern.compile("(?:[" +
      "\\u00AD" + // 173
      "\\u2012" + // 8210
      "\\u2013" + // 8211
      "\\u2014" + // 8212
      "\\u2015" + // 8213
      "\\u2212" + // 8722
      "])" +
      "|" + SPACE_REGEX,
    Pattern.UNICODE_CASE);

  private static final Pattern ILLEGAL_SPACE_PATTERN = Pattern.compile(SPACE_REGEX,
    Pattern.UNICODE_CASE);

  private String diagnosticMessageDash;
  private String diagnosticMessageSpace;

  @PostConstruct
  public void init() {
    diagnosticMessageDash = info.getResourceString("diagnosticMessageDash");
    diagnosticMessageSpace = info.getResourceString("diagnosticMessageSpace");
  }

  @Override
  public void check() {

    documentContext
      .getTokens()
      .stream()
      .filter((Token token) -> token.getChannel() == Lexer.HIDDEN
        || token.getType() == BSLLexer.STRINGPART
        || token.getType() == BSLLexer.STRING
        || token.getType() == BSLLexer.STRINGSTART
        || token.getType() == BSLLexer.STRINGTAIL
      )
      .filter((Token token) -> ILLEGAL_PATTERN.matcher(token.getText()).find())
      .forEach((Token token) ->
        {
          var text = token.getText();
          String message = diagnosticMessageDash;
          if (ILLEGAL_SPACE_PATTERN.matcher(text).find()) {
            message = diagnosticMessageSpace;
          }
          diagnosticStorage.addDiagnostic(token, message);
        }
      );
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.stream()
      .filter(diagnostic -> diagnostic.getMessage().equals(diagnosticMessageSpace))
      .forEach((Diagnostic diagnostic) -> {
        Range range = diagnostic.getRange();
        TextEdit textEdit = new TextEdit(range,
          ILLEGAL_SPACE_PATTERN.matcher(documentContext.getText(range)).replaceAll(" "));

        textEdits.add(textEdit);
      });

    diagnostics.stream()
      .filter(diagnostic -> diagnostic.getMessage().equals(diagnosticMessageDash))
      .forEach((Diagnostic diagnostic) -> {
        Range range = diagnostic.getRange();
        TextEdit textEdit = new TextEdit(range,
          ILLEGAL_PATTERN.matcher(documentContext.getText(range)).replaceAll("-"));

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
