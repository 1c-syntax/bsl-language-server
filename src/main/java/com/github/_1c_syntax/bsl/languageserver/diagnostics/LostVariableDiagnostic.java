/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.antlr.v4.runtime.tree.RuleNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNPREDICTABLE,
    DiagnosticTag.BADPRACTICE
  }

)

@RequiredArgsConstructor
public class LostVariableDiagnostic extends AbstractDiagnostic {

  private final ReferenceIndex referenceIndex;
  private final Map<MethodSymbol, BSLParser.SubContext> methodsAst = new HashMap<>();

  @Value
  private static class VarData implements Comparable<VarData> {
    String name;
    Range defRange;
    Range rewriteRange;
    List<Reference> references;
    MethodSymbol method;

    @Override
    public int compareTo(@NotNull LostVariableDiagnostic.VarData o) {
      return compare(this.getDefRange(), o.getDefRange());
    }
  }

  @Override
  protected void check() {
    documentContext.getSymbolTree().getMethods().stream()
      .flatMap(methodSymbol -> getVarData(methodSymbol).stream())
      .filter(this::isLostVariable)
      .forEach(this::fireIssue);

    methodsAst.clear();
  }

  private List<VarData> getVarData(MethodSymbol methodSymbol) {
    final var variables = methodSymbol.getChildren().stream()
      .filter(sourceDefinedSymbol -> sourceDefinedSymbol.getSymbolKind() == SymbolKind.Variable)
      .filter(VariableSymbol.class::isInstance)
      .map(VariableSymbol.class::cast)
      .flatMap(variable -> getVarData(variable, methodSymbol).stream())
      .collect(Collectors.toList());
    if (variables.isEmpty()){
      return Collections.emptyList();
    }
    return variables;
  }

  private List<VarData> getVarData(VariableSymbol variable, MethodSymbol methodSymbol) {
    List<Reference> allReferences = getSortedReferencesByLocation(variable);
    if (allReferences.isEmpty()) {
      return Collections.emptyList();
    }
    return getConsecutiveDefinitions(variable, allReferences, methodSymbol);
  }

  private List<Reference> getSortedReferencesByLocation(VariableSymbol variable) {
    final var references = referenceIndex.getReferencesTo(variable);
    return references.stream()
      .sorted((o1, o2) -> compare(o1.getSelectionRange(), o2.getSelectionRange()))
      .collect(Collectors.toList());
  }

  private List<VarData> getConsecutiveDefinitions(VariableSymbol variable, List<Reference> allReferences,
                                                  MethodSymbol methodSymbol) {
    List<VarData> result = new ArrayList<>();
    Reference prev = null;
    if (allReferences.get(0).getOccurrenceType() == OccurrenceType.DEFINITION) {
      prev = allReferences.get(0);

      var references = allReferences.subList(1, allReferences.size());
      var varData = new VarData(variable.getName(), variable.getVariableNameRange(),
        allReferences.get(0).getSelectionRange(), references, methodSymbol);
      result.add(varData);
    }
    for (var i = 1; i < allReferences.size(); i++) {
      final var next = allReferences.get(i);
      if (next.getOccurrenceType() == OccurrenceType.DEFINITION) {
        if (prev != null) {
          final List<Reference> references;
          if (i < allReferences.size() - 1) {
            references = allReferences.subList(i + 1, allReferences.size());
          } else {
            references = Collections.emptyList();
          }
          var varData = new VarData(variable.getName(), prev.getSelectionRange(),
            next.getSelectionRange(), references, methodSymbol);
          result.add(varData);
        }
        prev = next;
        continue;
      }
      prev = null;
    }
    return result;
  }

  private boolean isLostVariable(VarData varData) {
    final RuleNode defNode = findDefNode(varData);

    if (isForInside(defNode)){
      return false;
    }

    var defCodeBlockOpt = getCodeBlock(defNode);
    final var rewriteNodeInsideDefCodeBlockOpt = defCodeBlockOpt
      .flatMap(context -> Trees.findNodeContainsPosition(context,
        varData.rewriteRange.getStart()));
    if (rewriteNodeInsideDefCodeBlockOpt.isEmpty()) {
      return false;
    }

    var defCodeBlock = defCodeBlockOpt.orElseThrow();

    var rewriteNode = rewriteNodeInsideDefCodeBlockOpt.get();
    var rewriteStatement = getRootStatement(rewriteNode);
    var rewriteCodeBlock = getCodeBlock(rewriteStatement).orElseThrow();

    var isInsideSameBlock = defCodeBlock == rewriteCodeBlock;
    if (isInsideSameBlock) {
      return isLostVariableInSameBlock(varData, defNode, defCodeBlock, rewriteStatement);
    }

    return isLostVariableInDifferentBlocks(varData, rewriteStatement, rewriteCodeBlock);
  }

  private RuleNode findDefNode(VarData varData) {
    // быстрее сначала найти узел метода в дереве, а потом уже узел переменной в дереве метода
    // чтобы постоянно не искать по всему дереву файла
    final var methodCodeBlockContext = getMethodCodeBlockContext(varData.method);
    return Trees.findNodeContainsPosition(methodCodeBlockContext,
      varData.getDefRange().getStart())
      .orElseThrow();
  }

  private BSLParser.SubContext getMethodCodeBlockContext(MethodSymbol method) {
    return methodsAst.computeIfAbsent(method, methodSymbol ->
      Trees.findNodeContainsPosition(documentContext.getAst(),
          methodSymbol.getRange().getStart())
        .flatMap(node -> Trees.getRootNode(node, BSLParser.RULE_sub, BSLParser.SubContext.class))
        .orElseThrow());
  }

  private static  boolean isForInside(RuleNode defNode) {
    return defNode instanceof BSLParser.ForStatementContext || defNode instanceof BSLParser.ForEachStatementContext;
  }

  private static Optional<BSLParser.CodeBlockContext> getCodeBlock(RuleNode context) {
    return Trees.getRootNode(context, BSLParser.RULE_codeBlock, BSLParser.CodeBlockContext.class);
  }

  private static BSLParser.StatementContext getRootStatement(RuleNode node) {
    return Trees.getRootNode(node, BSLParser.RULE_statement, BSLParser.StatementContext.class)
      .orElseThrow();
  }

  private static boolean isLostVariableInSameBlock(VarData varData, RuleNode defNode,
                                                   BSLParser.CodeBlockContext defCodeBlock,
                                                   BSLParser.StatementContext rewriteStatement) {
    if (!varData.references.isEmpty() && isRewriteAlreadyContainsFirstReference(varData, rewriteStatement)) {
      return false;
    }
    var defStatement = getRootStatement(defNode);

    var defAndRewriteIsInSameLine = defStatement == rewriteStatement;
    if (defAndRewriteIsInSameLine){
      return true;
    }
    var hasPreprocessorBetween = getStatementsBetween(defCodeBlock, defStatement, rewriteStatement)
      .anyMatch(statementContext -> statementContext.preprocessor() != null);
    if (hasPreprocessorBetween){
      return false;
    }
    var hasReturnBetween = getStatementsBetween(defCodeBlock, defStatement, rewriteStatement)
      .anyMatch(statementContext -> Trees.findNodeSuchThat(statementContext, BSLParser.RULE_returnStatement)
        .isPresent());
    return !hasReturnBetween;
  }

  private static Stream<BSLParser.StatementContext> getStatementsBetween(BSLParser.CodeBlockContext defCodeBlock,
                                                                         BSLParser.StatementContext defStatement,
                                                                         BSLParser.StatementContext rewriteStatement) {
    return defCodeBlock.children.stream()
      .filter(BSLParser.StatementContext.class::isInstance)
      .map(BSLParser.StatementContext.class::cast)
      .dropWhile(statementContext -> statementContext != defStatement)
      .skip(1)
      .takeWhile(statementContext -> statementContext != rewriteStatement)
      ;
  }

  private static boolean isLostVariableInDifferentBlocks(VarData varData,
                                                         BSLParser.StatementContext rewriteStatement,
                                                         BSLParser.CodeBlockContext rewriteCodeBlock) {
    if (varData.references.isEmpty()) {
      return false;
    }
    if (isRewriteAlreadyContainsFirstReference(varData, rewriteStatement)) {
      return false;
    }
    return !hasReferenceOutsideRewriteBlock(varData.references, rewriteCodeBlock);
  }

  private static boolean isRewriteAlreadyContainsFirstReference(VarData varData,
                                                                BSLParser.StatementContext rewriteStatement) {
    return Ranges.containsRange(Ranges.create(rewriteStatement), varData.references.get(0).getSelectionRange());
  }

  private static boolean hasReferenceOutsideRewriteBlock(List<Reference> references, BSLParserRuleContext codeBlock) {
    return references.stream()
      .map(Reference::getSelectionRange)
      .anyMatch(range -> Trees.findTerminalNodeContainsPosition(codeBlock, range.getStart())
        .isEmpty());
  }

  private static int compare(Range o1, Range o2) {
    return compare(o1.getStart(), o2.getStart());
  }

  public static int compare(Position pos1, Position pos2) {
    // 1,1 10,10
    if (pos1.getLine() < pos2.getLine()) {
      return -1;
    }
    // 10,10 1,1
    if (pos1.getLine() > pos2.getLine()) {
      return 1;
    }
    // 1,4 1,9
    return Integer.compare(pos1.getCharacter(), pos2.getCharacter());
    // 1,9 1,4
  }

  private void fireIssue(VarData varData) {
    final var relatedInformationList = varData.getReferences().stream()
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        context.getSelectionRange(),
        "+1"
      )).collect(Collectors.toList());
    final var message = info.getMessage(varData.getName());
    diagnosticStorage.addDiagnostic(varData.getDefRange(), message, relatedInformationList);
  }
}
