/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import jakarta.annotation.PostConstruct;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;


@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.ALL,
  minutesToFix = 30,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.BRAINOVERLOAD
  }
)
public class NestedStatementsDiagnostic extends AbstractListenerDiagnostic {

  private String relatedMessage;
  private static final int MAX_ALLOWED_LEVEL = 4;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_ALLOWED_LEVEL
  )
  private int maxAllowedLevel = MAX_ALLOWED_LEVEL;

  private ParseTree lastCtx;
  private final Deque<ParseTree> nestedParents = new ArrayDeque<>();

  @PostConstruct
  public void init() {
    relatedMessage = this.info.getResourceString("parentStatementRelatedMessage");
  }

  @Override
  public void enterIfStatement(BSLParser.IfStatementContext ctx) {
    enterNode(ctx);
  }

  @Override
  public void exitIfStatement(BSLParser.IfStatementContext ctx) {
    exitNode(ctx);
  }

  @Override
  public void enterWhileStatement(BSLParser.WhileStatementContext ctx) {
    enterNode(ctx);
  }

  @Override
  public void exitWhileStatement(BSLParser.WhileStatementContext ctx) {
    exitNode(ctx);
  }

  @Override
  public void enterForStatement(BSLParser.ForStatementContext ctx) {
    enterNode(ctx);
  }

  @Override
  public void exitForStatement(BSLParser.ForStatementContext ctx) {
    exitNode(ctx);
  }

  @Override
  public void enterForEachStatement(BSLParser.ForEachStatementContext ctx) {
    enterNode(ctx);
  }

  @Override
  public void exitForEachStatement(BSLParser.ForEachStatementContext ctx) {
    exitNode(ctx);
  }

  @Override
  public void enterTryStatement(BSLParser.TryStatementContext ctx) {
    enterNode(ctx);
  }

  @Override
  public void exitTryStatement(BSLParser.TryStatementContext ctx) {
    exitNode(ctx);
  }

  private void enterNode(BSLParserRuleContext ctx) {
    lastCtx = ctx;
    nestedParents.push(ctx);
  }

  private void exitNode(BSLParserRuleContext ctx) {

    if (ctx == lastCtx && nestedParents.size() > maxAllowedLevel) {
      addRelatedInformationDiagnostic(ctx);
    }
    nestedParents.pop();
  }

  private void addRelatedInformationDiagnostic(BSLParserRuleContext ctx) {
    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();
    relatedInformation.add(
      RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(ctx.getStart()),
        relatedMessage
      )
    );

    nestedParents.stream()
      .filter(node -> node != ctx)
      .map(expressionContext ->
        RelatedInformation.create(
          documentContext.getUri(),
          Ranges.create(((BSLParserRuleContext) expressionContext).getStart()),
          relatedMessage
        )
      )
      .collect(Collectors.toCollection(() -> relatedInformation));

    diagnosticStorage.addDiagnostic(ctx.getStart(), relatedInformation);
  }
}
