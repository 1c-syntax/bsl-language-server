/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.mdo.MDLanguage;
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

  @Override
  public ParseTree visitTernaryOperator(BSLParser.TernaryOperatorContext ctx){

    var exp = ctx.expression();
    var condition = getBooleanToken(exp.get(0));
    var trueBranch = getBooleanToken(exp.get(1));
    var falseBranch = getBooleanToken(exp.get(2));

    if (condition != SKIPPED_RULE_INDEX) {
      diagnosticStorage.addDiagnostic(ctx);
    } else if (trueBranch == BSLParser.TRUE && falseBranch == BSLParser.FALSE){
      var dgs = diagnosticStorage.addDiagnostic(ctx);
      dgs.ifPresent(diagnostic -> diagnostic.setData(exp.get(0).getText()));
    } else if (trueBranch == BSLParser.FALSE && falseBranch == BSLParser.TRUE){
      var dgs = diagnosticStorage.addDiagnostic(ctx);
      dgs.ifPresent(diagnostic -> diagnostic.setData(getAdaptedText(exp.get(0).getText())));
    } else if (trueBranch != SKIPPED_RULE_INDEX || falseBranch != SKIPPED_RULE_INDEX){
      diagnosticStorage.addDiagnostic(ctx);
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
      var textEdit = new TextEdit(range,(String) diagnostic.getData());
      textEdits.add(textEdit);
    });

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );

  }

  private String getAdaptedText(String text) {
    if(documentContext.getServerContext().getConfiguration().getDefaultLanguage() == MDLanguage.ENGLISH) {
      return "NOT (" + text + ")";
    } else {
      return "НЕ (" + text + ")";
    }
  }

  private int getBooleanToken(BSLParser.ExpressionContext expCtx){

    return Optional.of(expCtx)
      .filter(ctx -> ctx.children.size() == 1)
      .map(ctx -> (BSLParser.MemberContext) ctx.getChild(0))
      .map(ctx -> ctx.getChild(0))
      .filter(BSLParser.ConstValueContext.class::isInstance)
      .map(BSLParser.ConstValueContext.class::cast)
      .map(ctx -> ctx.getToken(BSLParser.TRUE, 0))
      .map(ctx -> BSLParser.TRUE)
      .or(() -> Optional.of(expCtx)
          .filter(ctx -> ctx.children.size() == 1)
          .map(ctx -> (BSLParser.MemberContext) ctx.getChild(0))
          .map(ctx -> ctx.getChild(0))
          .filter(BSLParser.ConstValueContext.class::isInstance)
          .map(BSLParser.ConstValueContext.class::cast)
          .map(ctx -> ctx.getToken(BSLParser.FALSE, 0))
          .map(ctx -> BSLParser.FALSE)
        )
      .orElse(SKIPPED_RULE_INDEX);
  }
}
