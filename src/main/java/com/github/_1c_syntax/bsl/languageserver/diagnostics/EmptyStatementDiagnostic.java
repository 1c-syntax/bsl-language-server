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

import com.github._1c_syntax.bsl.languageserver.context.BSLDocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.providers.BSLCodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.BSLTrees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.ls_core.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.ls_core.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.ls_core.utils.Trees;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }
)
public class EmptyStatementDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx) {

    if (ctx.getChildCount() == 1
      && ctx.SEMICOLON() != null
      && !Trees.treeContainsErrors(
      Trees.getPreviousNode(
        Trees.getRootParent(ctx),
        ctx,
        BSLParser.RULE_statement))) {
      diagnosticStorage.addDiagnostic(ctx);
    }

    return super.visitStatement(ctx);
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    BSLDocumentContext documentContext
  ) {

    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.forEach((Diagnostic diagnostic) -> {

      Position diagnosticRangeEnd = diagnostic.getRange().getEnd();
      Position diagnosticRangeNewEnd = new Position(
        diagnosticRangeEnd.getLine(),
        diagnosticRangeEnd.getCharacter() - 1
      );

      Range range = new Range(diagnosticRangeNewEnd, diagnosticRangeEnd);

      TextEdit textEdit = new TextEdit(range, "");
      textEdits.add(textEdit);

    });

    return BSLCodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );

  }

}
