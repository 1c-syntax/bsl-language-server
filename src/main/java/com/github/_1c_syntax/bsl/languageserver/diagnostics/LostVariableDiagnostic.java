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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
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
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  private static final Set<VariableKind> GlobalVariableKinds = EnumSet.of(VariableKind.GLOBAL, VariableKind.MODULE);

  private final ReferenceIndex referenceIndex;
  private final Map<SourceDefinedSymbol, BSLParserRuleContext> astBySymbol = new HashMap<>();
  private Map<String, BSLParserRuleContext> methodContextsByMethodName = new CaseInsensitiveMap<>();

  @Value
  private static class VarData {
    VariableSymbol variable;
    Range defRange;
    Range rewriteRange;
    List<Reference> references;
    SourceDefinedSymbol parentSymbol;
    boolean isMethod;
  }

  @Override
  protected void check() {
    methodContextsByMethodName = getMethodContextsByMethodName();
    getFileCodeBlock()
      .ifPresent(fileCodeBlock -> methodContextsByMethodName.put("", fileCodeBlock));

    getVariables()
      .filter(this::isLostVariable)
      .forEach(this::fireIssue);

    astBySymbol.clear();
    methodContextsByMethodName.clear();
  }

  private Map<String, BSLParserRuleContext> getMethodContextsByMethodName() {
    return Optional.ofNullable(documentContext.getAst().subs())
      .map(BSLParser.SubsContext::sub)
      .map(subContexts -> subContexts.stream()
        .map(LostVariableDiagnostic::getMethodNameAndContext)
        .collect(Collectors.toMap(Pair::getLeft, Pair::getRight)))
      .orElseGet(Collections::emptyMap);
  }

  private static Pair<String, BSLParserRuleContext> getMethodNameAndContext(BSLParser.SubContext subContext) {
    final var subContextOpt = Optional.of(subContext);
    final var subName = subContextOpt
      .map(BSLParser.SubContext::function)
//      .map(functionContext -> Pair.of(functionContext, functionContext.funcDeclaration().subName()))
      .map(BSLParser.FunctionContext::funcDeclaration)
      .map(BSLParser.FuncDeclarationContext::subName)
      .map(BSLParser.SubNameContext::IDENTIFIER)
      .map(ParseTree::getText)
      .orElseGet(() -> subContextOpt
        .map(BSLParser.SubContext::procedure)
        .map(BSLParser.ProcedureContext::procDeclaration)
        .map(BSLParser.ProcDeclarationContext::subName)
        .map(BSLParser.SubNameContext::IDENTIFIER)
        .map(ParseTree::getText)
        .orElseThrow()
      );
    return Pair.of(subName, subContext);
  }

  private Optional<BSLParser.FileCodeBlockContext> getFileCodeBlock() {
    return Optional.ofNullable(documentContext.getAst().fileCodeBlock());
  }

  private Stream<VarData> getVariables() {
    return documentContext.getSymbolTree().getVariables().stream()
      .map(variableSymbol -> Pair.of(getRootSymbol(variableSymbol), variableSymbol))
      .flatMap(pair -> getVarData(pair.getRight(), pair.getLeft()).stream());
  }

  private static SourceDefinedSymbol getRootSymbol(VariableSymbol variableSymbol) {
    return variableSymbol.getRootParent(SymbolKind.Method)
      .orElseGet(() -> variableSymbol.getRootParent(SymbolKind.Module)
        .orElseThrow());
  }

  private List<VarData> getVarData(VariableSymbol variable, SourceDefinedSymbol methodSymbol) {
    List<Reference> allReferences = referenceIndex.getReferencesTo(variable);
    if (allReferences.isEmpty()) {
      return Collections.emptyList();
    }
    return getConsecutiveDefinitions(variable, allReferences, methodSymbol);
  }

  private List<VarData> getConsecutiveDefinitions(VariableSymbol variable, List<Reference> allReferences,
                                                  SourceDefinedSymbol methodSymbol) {
    List<VarData> result = new ArrayList<>();
    Reference prev = null;
    final var isGlobalVar = GlobalVariableKinds.contains(variable.getKind());
    if (!isGlobalVar && allReferences.get(0).getOccurrenceType() == OccurrenceType.DEFINITION) {
      prev = allReferences.get(0);

      var references = allReferences.subList(1, allReferences.size());
      var varData = new VarData(variable, variable.getVariableNameRange(),
        allReferences.get(0).getSelectionRange(), references, methodSymbol, methodSymbol instanceof MethodSymbol);
      result.add(varData);
    }
    final int firstIndex;
    if (isGlobalVar){
      firstIndex = 0;
    } else {
      firstIndex = 1;
    }
    for (var i = firstIndex; i < allReferences.size(); i++) {
      final var current = allReferences.get(i);
      if (current.getOccurrenceType() == OccurrenceType.DEFINITION) {
        if (prev != null) {
          final List<Reference> references;
          if (i < allReferences.size() - 1) {
            references = allReferences.subList(i + 1, allReferences.size());
          } else {
            references = Collections.emptyList();
          }
          var varData = new VarData(variable, prev.getSelectionRange(),
            current.getSelectionRange(), references, methodSymbol, methodSymbol instanceof MethodSymbol);
          result.add(varData);
        }
        prev = current;
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
      .flatMap(context -> Trees.findContextContainsPosition(context, varData.rewriteRange.getStart()));
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
    final var parentBlockContext = getMethodCodeBlockContext(varData.parentSymbol, varData.isMethod);

    return Trees.findContextContainsPosition(parentBlockContext, varData.getDefRange().getStart())
      .orElseGet(() -> findMethodByRange(varData.defRange));
  }

  private BSLParserRuleContext getMethodCodeBlockContext(SourceDefinedSymbol method, boolean isMethod) {
    if (isMethod) {
      return astBySymbol.computeIfAbsent(method, methodSymbol ->
          methodContextsByMethodName.get(methodSymbol.getName()));
    }
    return astBySymbol.computeIfAbsent(method, methodSymbol ->
      methodContextsByMethodName.get(""));
  }

  private BSLParserRuleContext findMethodByRange(Range range) {
    final var methodContext = documentContext.getSymbolTree().getMethods().stream()
      .filter(methodSymbol -> Ranges.containsRange(methodSymbol.getRange(), range))
      .map(methodSymbol -> methodContextsByMethodName.get(methodSymbol.getName()))
      .filter(Objects::nonNull)
      .findFirst()
      .orElseThrow();
    return Trees.findContextContainsPosition(methodContext, range.getStart())
      .orElseThrow();
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

  private void fireIssue(VarData varData) {
    var resultRefs = new ArrayList<DiagnosticRelatedInformation>();
    resultRefs.add(RelatedInformation.create(
      documentContext.getUri(),
      varData.rewriteRange,
      "+1"));
    resultRefs.addAll(varData.getReferences().stream()
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        context.getSelectionRange(),
        "+1"
      )).collect(Collectors.toList()));
    final var message = info.getMessage(varData.variable.getName());
    diagnosticStorage.addDiagnostic(varData.getDefRange(), message, resultRefs);
  }
}
