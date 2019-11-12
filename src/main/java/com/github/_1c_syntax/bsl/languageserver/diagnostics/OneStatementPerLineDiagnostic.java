/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.DESIGN
  }
)
public class OneStatementPerLineDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {
  private static final Pattern NEW_LINE_PATTERN = Pattern.compile(
    "^(\\s+?)[^\\s]",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private int previousLineNumber;
  private int previousDiagnosticLineNumber;

  public OneStatementPerLineDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx) {

    if (ctx.preprocessor() != null) {
      return super.visitStatement(ctx);
    }

    if (ctx.getChildCount() == 1 && ctx.SEMICOLON() != null) {
      return super.visitStatement(ctx);
    }

    int currentLine = ctx.getStart().getLine();

    if (currentLine == previousLineNumber && currentLine != previousDiagnosticLineNumber) {
      diagnosticStorage.addDiagnostic(ctx);
      previousDiagnosticLineNumber = currentLine;
    }

    previousLineNumber = currentLine;

    return super.visitStatement(ctx);
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {
    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.forEach((Diagnostic diagnostic) -> {
      Range range = diagnostic.getRange();
      Range startLineRange = Ranges.create(
        range.getStart().getLine(),
        0,
        range.getStart().getLine(),
        range.getStart().getCharacter());

      Matcher matcher = NEW_LINE_PATTERN.matcher(documentContext.getText(startLineRange));
      String indent = "";
      if (matcher.find()) {
        indent = matcher.group(1);
      }

      TextEdit textEdit = new TextEdit(
        range, "\n" + indent + documentContext.getText(range)
      );
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
