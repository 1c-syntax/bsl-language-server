/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.SUSPICIOUS
  }
)
public class UselessTernaryOperatorDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

  private static final int SKIPPED_RULE_INDEX = 0;
  private static final int COUNT_EXPRESSIONS = 3;
  private static final int INDEX_CONDITION = 0;
  private static final int INDEX_TRUE_BRANCH = 1;
  private static final int INDEX_FALSE_BRANCH = 2;

  @Override
  public ParseTree visitTernaryOperator(BSLParser.TernaryOperatorContext ctx) {
    var exp = ctx.expression();

    if (exp != null && exp.size() >= COUNT_EXPRESSIONS) {
      var condition = getBooleanToken(exp.get(INDEX_CONDITION));
      var trueBranch = getBooleanToken(exp.get(INDEX_TRUE_BRANCH));
      var falseBranch = getBooleanToken(exp.get(INDEX_FALSE_BRANCH));

      if (condition != SKIPPED_RULE_INDEX) {
        diagnosticStorage.addDiagnostic(ctx);
      } else if (trueBranch == BSLParser.TRUE && falseBranch == BSLParser.FALSE) {
        diagnosticStorage.addDiagnostic(ctx, DiagnosticStorage.createAdditionalData(exp.get(INDEX_CONDITION).getText()));
      } else if (trueBranch == BSLParser.FALSE && falseBranch == BSLParser.TRUE) {
        diagnosticStorage.addDiagnostic(ctx,
          DiagnosticStorage.createAdditionalData(getAdaptedText(exp.get(INDEX_CONDITION).getText())));
      } else if (trueBranch != SKIPPED_RULE_INDEX || falseBranch != SKIPPED_RULE_INDEX) {
        diagnosticStorage.addDiagnostic(ctx);
      } else {
        // остальное - не ошибка
      }
    }

    return super.visitTernaryOperator(ctx);
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.forEach((Diagnostic diagnostic) -> {
      var range = diagnostic.getRange();
      var data = diagnostic.getData();
      if (data instanceof DiagnosticStorage.DiagnosticAdditionalData additionalData) {
        var textEdit = new TextEdit(range, additionalData.string());
        textEdits.add(textEdit);
      }
    });

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );

  }

  private String getAdaptedText(String text) {
    return info.getResourceString("quickFixAdaptedText", text);
  }

  private static int getBooleanToken(BSLParser.ExpressionContext expCtx) {

    var tmpCtx = Optional.of(expCtx)
      .filter(ctx -> ctx.children.size() == 1)
      .map(ctx -> ctx.member(0))
      .map(ctx -> ctx.getChild(0))
      .filter(BSLParser.ConstValueContext.class::isInstance)
      .map(BSLParser.ConstValueContext.class::cast);

    return tmpCtx
      .map(ctx -> ctx.getToken(BSLParser.TRUE, 0))
      .map(ctx -> BSLParser.TRUE)
      .or(() -> tmpCtx
        .map(ctx -> ctx.getToken(BSLParser.FALSE, 0))
        .map(ctx -> BSLParser.FALSE)
      )
      .orElse(SKIPPED_RULE_INDEX);
  }
}
