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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  },
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_1
)
public class CodeOutOfRegionDiagnostic extends AbstractVisitorDiagnostic {
  private final List<Range> regionsRanges = new ArrayList<>();

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {

    // Для неизвестных модулей не будем требовать нахождения кода в области
    if (documentContext.getModuleType() == ModuleType.UNKNOWN) {
      return ctx;
    }

    List<RegionSymbol> regions = documentContext.getSymbolTree().getModuleLevelRegions();
    regionsRanges.clear();

    // если областей нет, то и смысла дальше анализировать тоже нет
    if (regions.isEmpty() && !ctx.getTokens().isEmpty()) {

      List<DiagnosticRelatedInformation> relatedInformation = createRelatedInformations(ctx);
      if (!relatedInformation.isEmpty()) {
        diagnosticStorage.addDiagnostic(
          relatedInformation.get(0).getLocation().getRange(),
          relatedInformation);
      }
      return ctx;
    }

    regions.forEach(region -> regionsRanges.add(region.getRange()));

    return super.visitFile(ctx);

  }

  private List<DiagnosticRelatedInformation> createRelatedInformations(BSLParser.FileContext ctx) {
    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

    // замечание будет на первой строке модуля, остальные блоки - в релейшенах

    // 1. блок переменных
    // 2. блок кода до методов
    addChildrenToRelatedInformation(ctx, relatedInformation,
      BSLParser.RULE_moduleVars, BSLParser.RULE_fileCodeBlockBeforeSub);

    // 3. методы
    documentContext.getSymbolTree().getMethods().stream()
      .map(node ->
        RelatedInformation.create(
          documentContext.getUri(),
          node.getSubNameRange(),
          "+1"
        )
      )
      .collect(Collectors.toCollection(() -> relatedInformation));

    // 4. блок кода после методов
    addChildrenToRelatedInformation(ctx, relatedInformation, BSLParser.RULE_fileCodeBlock);
    return relatedInformation;
  }

  private void addChildrenToRelatedInformation(
    BSLParser.FileContext ctx,
    List<DiagnosticRelatedInformation> relatedInformation,
    Integer... ruleIndex
  ) {
    Trees.getChildren(ctx, ruleIndex).stream()
      .filter(node -> !node.getTokens().isEmpty())
      .map(node ->
        RelatedInformation.create(
          documentContext.getUri(),
          Ranges.create(node),
          "+1"
        )
      )
      .collect(Collectors.toCollection(() -> relatedInformation));
  }

  @Override
  public ParseTree visitModuleVar(BSLParser.ModuleVarContext ctx) {
    Trees.getChildren(ctx).stream()
      .filter(node -> !(node instanceof BSLParser.PreprocessorContext)
        && !(node instanceof TerminalNode))
      .findFirst()
      .ifPresent((Tree node) -> {
          Range ctxRange = Ranges.create((BSLParserRuleContext) node);
          if (regionsRanges.stream().noneMatch(regionRange ->
            Ranges.containsRange(regionRange, ctxRange))) {
            diagnosticStorage.addDiagnostic(ctx);
          }
        }
      );
    return ctx;
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    documentContext.getSymbolTree().getMethodSymbol(ctx).ifPresent((MethodSymbol methodSymbol) -> {
      if (methodSymbol.getRegion().isEmpty()) {
        diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange());
      }
    });
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    addDiagnosticForFileCodeBlock(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlockBeforeSub(BSLParser.FileCodeBlockBeforeSubContext ctx) {
    addDiagnosticForFileCodeBlock(ctx);
    return ctx;
  }

  private void addDiagnosticForFileCodeBlock(BSLParserRuleContext ctx) {
    Trees.findAllRuleNodes(ctx, BSLParser.RULE_statement)
      .stream()
      .filter(node -> node.getParent().getParent() == ctx)
      .forEach((ParseTree child) -> {
        if (child.getChildCount() > 1
          || !(child.getChild(0) instanceof BSLParser.PreprocessorContext)) {
          Range ctxRange = Ranges.create((BSLParser.StatementContext) child);
          if (regionsRanges.stream().noneMatch(regionRange ->
            Ranges.containsRange(regionRange, ctxRange))) {
            diagnosticStorage.addDiagnostic((BSLParser.StatementContext) child);
          }
        }
      });
  }
}
