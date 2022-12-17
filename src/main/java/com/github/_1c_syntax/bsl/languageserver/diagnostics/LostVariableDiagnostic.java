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
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
  },
  // TODO сделать флаг для управления работой в модулях объектов и форм? ведь в них могут быть FP
  modules = {
    ModuleType.CommandModule,
    ModuleType.CommonModule,
    ModuleType.ManagerModule,
    ModuleType.ValueManagerModule,
    ModuleType.SessionModule,
    ModuleType.UNKNOWN
  }
)

@RequiredArgsConstructor
public class LostVariableDiagnostic extends AbstractDiagnostic {

  private static final Set<VariableKind> GlobalVariableKinds = EnumSet.of(VariableKind.GLOBAL, VariableKind.MODULE);
  private static final String MODULE_SCOPE_NAME = "";
  private static final String UNUSEDAFTER_MESSAGE = "unusedAfterMessage";

  private static final Collection<Integer> EXCLUDED_TOP_RULE_FOR_LOOP = Set.of(BSLParser.RULE_subCodeBlock, BSLParser.RULE_fileCodeBlock);
  private static final Collection<Integer> LOOPS;

  private final ReferenceIndex referenceIndex;
  private final Map<SourceDefinedSymbol, BSLParserRuleContext> astBySymbol = new HashMap<>();
  private Map<String, BSLParserRuleContext> methodContextsByMethodName;

  static {
    final var loops = new HashSet<>(EXCLUDED_TOP_RULE_FOR_LOOP);
    loops.addAll(Set.of(BSLParser.RULE_forStatement, BSLParser.RULE_forEachStatement, BSLParser.RULE_whileStatement));
    LOOPS = Set.copyOf(loops);
  }

  @Override
  protected void check() {
    methodContextsByMethodName = getMethodContextsByMethodName();
    getFileCodeBlock()
      .ifPresent(fileCodeBlock -> methodContextsByMethodName.put(MODULE_SCOPE_NAME, fileCodeBlock));

    getVariables()
      .filter(this::isLostVariable)
      .forEach(this::fireIssue);

    astBySymbol.clear();
    methodContextsByMethodName.clear();
  }

  private Map<String, BSLParserRuleContext> getMethodContextsByMethodName() {
    if (documentContext.getSymbolTree().getMethods().isEmpty()){
      return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }
    return Optional.ofNullable(documentContext.getAst().subs())
      .map(BSLParser.SubsContext::sub)
      .map(subContexts -> subContexts.stream()
        .map(LostVariableDiagnostic::getMethodNameAndContext)
        .collect(Collectors.toMap(Pair::getLeft, Pair::getRight)))
      .orElseThrow();
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

  private static List<VarData> getConsecutiveDefinitions(VariableSymbol variable, List<Reference> allReferences,
                                                  SourceDefinedSymbol methodSymbol) {
    List<VarData> result = new ArrayList<>();
    Reference prev = null;
    final var isGlobalVar = GlobalVariableKinds.contains(variable.getKind());
    if (!isGlobalVar && allReferences.get(0).getOccurrenceType() == OccurrenceType.DEFINITION) {
      prev = allReferences.get(0);

      var references = allReferences.subList(1, allReferences.size());
      var varData = VarData.of(variable, variable.getVariableNameRange(), allReferences.get(0),
                                                      methodSymbol, references);
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
          var varData = VarData.of(variable, prev.getSelectionRange(), current,
                                                          methodSymbol, references);
          result.add(varData);
        }
        prev = current;
        continue;
      }
      prev = null;
    }
    if (!isGlobalVar && variable.getKind() != VariableKind.PARAMETER){
      final var lastDefinition = getLastDefinition(allReferences);
      if (lastDefinition != null){
        result.add(VarData.ofFinished(variable, lastDefinition, methodSymbol));
      }
    }
    return result;
  }

  @Nullable
  private static Reference getLastDefinition(List<Reference> referencesTo) {
    var reverseIterator = referencesTo.listIterator(referencesTo.size());

    if(reverseIterator.hasPrevious()) {
      final var ref = reverseIterator.previous();
      if (ref.getOccurrenceType() == OccurrenceType.DEFINITION){
        return ref;
      }
    }
    return null;
  }

  private boolean isLostVariable(VarData varData) {
    final RuleNode defNode = findDefNode(varData);

    final var codeBlockForLoopIndex = codeBlockForLoopIndex(defNode);
    if (codeBlockForLoopIndex.isPresent()) {
      // пропускаю неиспользуемый итератор или счетчик цикла, т.е. есть существующее правило
      final var isSameLoop = Ranges.compare(varData.defRange, varData.rewriteRange) == 0 && varData.references.isEmpty();
      final var isInnerLoop = Ranges.containsRange(Ranges.create(codeBlockForLoopIndex.get()), varData.rewriteRange);
      if (isSameLoop || !isInnerLoop) {
        return false;
      }
    } else if (varData.unusedAfter){
      return true;
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
    final BSLParserRuleContext parentBlockContext;
    if (varData.isGlobalOrModuleKind) {
      parentBlockContext = findCodeBlockContextByRange(varData.defRange);
    } else {
      // быстрее сначала найти узел метода в дереве, а потом уже узел переменной в дереве метода
      // чтобы постоянно не искать по всему дереву файла
      parentBlockContext = getCodeBlockContextBySymbol(varData.parentSymbol, varData.isMethod);
    }
    return Trees.findContextContainsPosition(parentBlockContext, varData.defRange.getStart())
      .orElseThrow();
  }

  private BSLParserRuleContext findCodeBlockContextByRange(Range range) {
    final var methodName = documentContext.getSymbolTree().getMethods().stream()
      .filter(methodSymbol -> Ranges.containsRange(methodSymbol.getRange(), range))
      .map(MethodSymbol::getName)
      .findFirst()
      .orElse(MODULE_SCOPE_NAME);
    return Optional.ofNullable(methodContextsByMethodName.get(methodName))
      .filter(Objects::nonNull)
      .orElseThrow();
  }

  private BSLParserRuleContext getCodeBlockContextBySymbol(SourceDefinedSymbol method, boolean isMethod) {
    if (isMethod) {
      return astBySymbol.computeIfAbsent(method, methodSymbol ->
          methodContextsByMethodName.get(methodSymbol.getName()));
    }
    return astBySymbol.computeIfAbsent(method, methodSymbol ->
      methodContextsByMethodName.get(MODULE_SCOPE_NAME));
  }

  private static Optional<BSLParser.CodeBlockContext> codeBlockForLoopIndex(RuleNode defNode) {
    if (defNode instanceof BSLParser.ForStatementContext){
      return Optional.of(((BSLParser.ForStatementContext) defNode)
        .codeBlock());
    } else if (defNode instanceof BSLParser.ForEachStatementContext){
      return Optional.of(((BSLParser.ForEachStatementContext) defNode)
        .codeBlock());
    }
    return Optional.empty();
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
    if (!varData.references.isEmpty() && isRewriteAlreadyContainsFirstReference(varData, rewriteStatement)) {
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
    final String message = getMessage(varData);
    diagnosticStorage.addDiagnostic(varData.getDefRange(), message, getDiagnosticReferences(varData));
  }

  private String getMessage(VarData varData) {
    if (varData.unusedAfter){
      return info.getResourceString(UNUSEDAFTER_MESSAGE, varData.variable.getName());
    }
    return info.getMessage(varData.variable.getName());
  }

  private List<DiagnosticRelatedInformation> getDiagnosticReferences(VarData varData) {
    final var references = varData.getReferences().stream()
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        context.getSelectionRange(),
        "+1"
      )).collect(Collectors.toList());
    if (varData.unusedAfter) {
      return references;
    }
    final var result = new ArrayList<DiagnosticRelatedInformation>();
    result.add(RelatedInformation.create(
      documentContext.getUri(),
      varData.rewriteRange,
      "+1"));
    result.addAll(references);
    return result;
  }

  @Value
  private static class VarData {
    VariableSymbol variable;
    Range defRange;
    Range rewriteRange;
    List<Reference> references;
    SourceDefinedSymbol parentSymbol;
    boolean isMethod;
    boolean isGlobalOrModuleKind;
    boolean unusedAfter;

    private static VarData of(VariableSymbol variable, Range defRange, Reference rewriteReference,
                              SourceDefinedSymbol methodSymbol, List<Reference> references) {
      return new VarData(variable, defRange,
        rewriteReference.getSelectionRange(), references, methodSymbol, methodSymbol instanceof MethodSymbol,
        GlobalVariableKinds.contains(variable.getKind()), false);
    }

    private static VarData ofFinished(VariableSymbol variable, Reference lastDefinition, SourceDefinedSymbol methodSymbol) {
      return new VarData(variable, lastDefinition.getSelectionRange(), lastDefinition.getSelectionRange(),
        Collections.emptyList(), methodSymbol, methodSymbol instanceof MethodSymbol,
        GlobalVariableKinds.contains(variable.getKind()), true);
    }
  }
}
