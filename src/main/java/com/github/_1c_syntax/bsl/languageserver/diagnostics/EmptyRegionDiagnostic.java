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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class EmptyRegionDiagnostic extends AbstractListenerDiagnostic implements QuickFixProvider {
  private int currentRegionLevel;
  private int currentUsageLevel;
  private final Deque<BSLParser.RegionStartContext> regions = new ArrayDeque<>();

  @Override
  public void enterEveryRule(ParserRuleContext ctx) {
    if (ctx instanceof BSLParser.RegionStartContext) {
      currentRegionLevel++;
      regions.push((BSLParser.RegionStartContext) ctx);
    } else if (!(ctx instanceof BSLParser.PreprocessorContext
      || ctx instanceof BSLParser.RegionNameContext
      || ctx instanceof BSLParser.RegionEndContext
      || ctx instanceof BSLParser.StatementContext)
      && currentUsageLevel < currentRegionLevel) {
      currentUsageLevel = currentRegionLevel;
    }
  }

  @Override
  public void exitEveryRule(ParserRuleContext ctx) {
    if (ctx instanceof BSLParser.RegionEndContext && !regions.isEmpty()) {
      BSLParser.RegionStartContext currentRegion = regions.pop();
      if (currentUsageLevel < currentRegionLevel) {
        diagnosticStorage.addDiagnostic(
          Ranges.create(currentRegion.getParent(), ctx),
          info.getMessage(currentRegion.regionName().getText())
        );
      } else if (currentRegionLevel == currentUsageLevel) {
        currentUsageLevel--;
      }
      currentRegionLevel--;
    }
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {
    List<TextEdit> textEdits = new ArrayList<>(diagnostics.size());
    diagnostics
      .stream()
      .map(Diagnostic::getRange)
      .sorted(Comparator.comparingInt(o -> o.getStart().getLine()))
      .reduce((Range prev, Range curr) -> {
        if (prev.getEnd().getLine() > curr.getStart().getLine()) {
          return prev;
        } else {
          textEdits.add(new TextEdit(prev, ""));
          return curr;
        }
      })
      .ifPresent(lastRange -> textEdits.add(new TextEdit(lastRange, "")));

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }
}
