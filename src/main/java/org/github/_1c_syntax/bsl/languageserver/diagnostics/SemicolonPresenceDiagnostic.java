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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1
)
public class SemicolonPresenceDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx) {

    if (ctx.preprocessor() == null && ctx.SEMICOLON() == null) {
      Token lastToken = ctx.getStop();
      if (lastToken != null) {
        diagnosticStorage.addDiagnostic(lastToken);
      }
    }
    return super.visitStatement(ctx);
  }

  @Override
  public List<CodeAction> getQuickFixes(
    Diagnostic diagnostic,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    Range diagnosticRange = diagnostic.getRange();
    Position diagnosticRangeEnd = diagnosticRange.getEnd();

    Range range = new Range(diagnosticRangeEnd, diagnosticRangeEnd);

    return CodeActionProvider.createCodeActions(
      range,
      ";",
      getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostic
    );

  }
}
