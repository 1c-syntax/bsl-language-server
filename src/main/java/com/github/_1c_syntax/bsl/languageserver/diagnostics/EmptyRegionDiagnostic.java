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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class EmptyRegionDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  private static final Set<Integer> REGIONS_NODE_INDEXES = Set.of(
    BSLParser.RULE_regionName,
    BSLParser.RULE_regionStart,
    BSLParser.RULE_regionEnd,
    BSLParser.RULE_preprocessor
  );

  private final List<BSLParserRuleContext> allNodes = new ArrayList<>();

  public EmptyRegionDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {

    allNodes.clear();

    Trees.getDescendants(documentContext.getAst()).stream()
      .filter(node -> node instanceof BSLParserRuleContext)
      .map(node -> (BSLParserRuleContext) node)
      .filter(node -> (node.getStop() != null)
        && (node.getStart() != null))
      .collect(Collectors.toCollection(() -> allNodes));

    documentContext.getSymbolTree().getRegionsFlat().forEach(this::checkRegion);

    allNodes.clear();
  }

  private void checkRegion(RegionSymbol region) {
    // zero-based ranges
    int startLine = region.getStartRange().getStart().getLine() + 1;
    int endLine = region.getEndRange().getEnd().getLine() + 1;

    var hasChildren = allNodes.stream()
      .filter(node ->
        node.getStart().getLine() > startLine
          && node.getStart().getLine() < endLine)
      .map(RuleContext::getRuleIndex)
      .filter(ruleIndex -> !REGIONS_NODE_INDEXES.contains(ruleIndex))
      .findAny();

    if (hasChildren.isEmpty()) {
      diagnosticStorage.addDiagnostic(
        region.getStartRange(),
        info.getMessage(region.getName())
      );
    }
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {
    diagnostics.sort(Comparator.comparingInt(o -> o.getRange().getStart().getLine()));
    List<TextEdit> textEdits = new ArrayList<>();
    int maxDiagnosticEndLine = 0;

    for (Diagnostic diagnostic : diagnostics) {

      int diagnosticStartLine = diagnostic.getRange().getStart().getLine();

      Optional<RegionSymbol> optionalRegionSymbol = documentContext.getSymbolTree().getRegionsFlat()
        .stream()
        .filter(regionSymbol -> regionSymbol.getRange().getStart().getLine() == diagnosticStartLine)
        .findFirst();
      if (optionalRegionSymbol.isEmpty()) {
        continue;
      }
      RegionSymbol region = optionalRegionSymbol.get();

      int diagnosticEndLine = region.getRange().getEnd().getLine() - 1;
      if (diagnosticEndLine < maxDiagnosticEndLine) {
        continue;
      }

      if (maxDiagnosticEndLine == 0 || diagnosticEndLine > maxDiagnosticEndLine) {
        maxDiagnosticEndLine = diagnosticEndLine;
      }

      int diagnosticStartCharacter = diagnostic.getRange().getStart().getCharacter() - 1;
      Position diagnosticRangeStart = new Position(diagnosticStartLine, diagnosticStartCharacter);

      Position diagnosticRangeEnd = new Position(
        diagnosticEndLine + 1,
        0
      );

      Range range = new Range(diagnosticRangeStart, diagnosticRangeEnd);

      TextEdit textEdit = new TextEdit(range, "");
      textEdits.add(textEdit);

    }

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }
}

