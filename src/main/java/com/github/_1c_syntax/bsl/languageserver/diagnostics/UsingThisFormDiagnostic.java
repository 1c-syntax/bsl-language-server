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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.FormModule
  },
  minutesToFix = 1,
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_3,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.DEPRECATED
  }
)
public class UsingThisFormDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

  private static final Pattern PATTERN = CaseInsensitivePattern.compile("^(этаформа|thisform)");
  private static final Pattern ONLY_RU_PATTERN = CaseInsensitivePattern.compile("этаформа");
  private static final String THIS_OBJECT = "ЭтотОбъект";
  private static final String THIS_OBJECT_EN = "ThisObject";

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    if (needCheck(ctx.procDeclaration())) {
      return super.visitProcedure(ctx);
    }
    return ctx;
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    if (needCheck(ctx.funcDeclaration())) {
      return super.visitFunction(ctx);
    }
    return ctx;
  }

  private static boolean needCheck(ParserRuleContext declaration) {
    List<? extends BSLParser.ParamContext> params = getParams(declaration);
    return params.isEmpty() || !hasThisForm(params);
  }

  private static List<? extends BSLParser.ParamContext> getParams(ParserRuleContext declaration) {
    var paramList = declaration.getRuleContext(BSLParser.ParamListContext.class, 0);
    if (paramList == null) {
      return Collections.emptyList();
    }
    return paramList.getRuleContexts(BSLParser.ParamContext.class);
  }

  private static boolean hasThisForm(List<? extends BSLParser.ParamContext> params) {
    for (BSLParser.ParamContext param : params) {
      if (PATTERN.matcher(param.getText()).find()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {
    if (ctx.globalMethodCall() != null
      && ctx.getStart() == ctx.globalMethodCall().getStart()) {
      return super.visitCallStatement(ctx);
    }

    if (PATTERN.matcher(ctx.getStart().getText()).matches()) {
      diagnosticStorage.addDiagnostic(ctx.getStart());
    }

    return super.visitCallStatement(ctx);
  }

  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    Trees.findAllTokenNodes(ctx, BSLParser.IDENTIFIER).stream()
      .filter(token -> PATTERN.matcher(token.getText()).matches())
      .forEach(token -> diagnosticStorage.addDiagnostic((TerminalNode) token));

    return ctx;
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    List<TextEdit> newTextEdits = new ArrayList<>();

    for (Diagnostic diagnostic : diagnostics) {
      newTextEdits.add(getQuickFixText(diagnostic, documentContext));
    }

    return CodeActionProvider.createCodeActions(
      newTextEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }

  @Override
  public ParseTree visitLValue(BSLParser.LValueContext ctx) {

    TerminalNode identifier = ctx.IDENTIFIER();
    if (identifier != null && PATTERN.matcher(identifier.getText()).matches()) {
      diagnosticStorage.addDiagnostic(identifier);
    }
    return super.visitLValue(ctx);
  }

  private static TextEdit getQuickFixText(Diagnostic diagnostic, DocumentContext documentContext) {
    Range range = diagnostic.getRange();
    String currentText = documentContext.getText(range);

    if (ONLY_RU_PATTERN.matcher(currentText).matches()) {
      return new TextEdit(range, THIS_OBJECT);
    }

    return new TextEdit(range, THIS_OBJECT_EN);
  }
}
