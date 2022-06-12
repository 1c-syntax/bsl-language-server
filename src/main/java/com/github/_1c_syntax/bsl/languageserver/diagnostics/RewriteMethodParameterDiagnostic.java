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
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)

@RequiredArgsConstructor
public class RewriteMethodParameterDiagnostic extends AbstractDiagnostic {
  public static final int RULE_ASSIGNMENT = BSLParser.RULE_assignment;
  private static final Set<Integer> ROOTS_OF_DEF_OR_REFS = Set.of(
    RULE_ASSIGNMENT, BSLParser.RULE_statement, BSLParser.RULE_subCodeBlock, BSLParser.RULE_fileCodeBlock
  );
  private final ReferenceIndex referenceIndex;

  @Override
  public void check() {
    documentContext.getSymbolTree().getMethods().stream()
      .flatMap(RewriteMethodParameterDiagnostic::getParametersByValue)
      .flatMap(pair -> getVariableByParameter(pair.getLeft(), pair.getRight()))
      .map(this::isOverwrited)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .forEach(variableSymbolListPair -> fireIssue(variableSymbolListPair.getLeft(), variableSymbolListPair.getRight()));

  }

  private static Stream<Pair<MethodSymbol, ParameterDefinition>> getParametersByValue(MethodSymbol methodSymbol) {
    return methodSymbol.getParameters().stream()
      .filter(ParameterDefinition::isByValue)
      .map(parameterDefinition -> Pair.of(methodSymbol, parameterDefinition));
  }

  private static Stream<VariableSymbol> getVariableByParameter(MethodSymbol method, ParameterDefinition parameterDefinition) {
    return method.getChildren().stream()
      .filter(sourceDefinedSymbol -> sourceDefinedSymbol.getSymbolKind() == SymbolKind.Variable)
      .filter(variable -> parameterDefinition.getRange().getStart().equals(variable.getSelectionRange().getStart()))
      .filter(VariableSymbol.class::isInstance)
      .map(VariableSymbol.class::cast)
      .findFirst().stream();
  }

  private Optional<Pair<VariableSymbol, List<Reference>>> isOverwrited(VariableSymbol variable) {
    final var references = getSortedReferencesByLocation(variable);
    if (isOverwrited(references)) {
      return Optional.of(Pair.of(variable, references));
    }
    return Optional.empty();
  }

  private boolean isOverwrited(List<Reference> references) {
    if (!references.isEmpty()) {
      final var firstDefIntoAssign = references.get(0);
      if (firstDefIntoAssign.getOccurrenceType() == OccurrenceType.DEFINITION) {
        if (references.size() == 1) {
          return true;
        }
        return noneWritingToDefOrSelfAssign(firstDefIntoAssign, references.get(1));
      }
    }
    return false;
  }

  private List<Reference> getSortedReferencesByLocation(VariableSymbol variable) {
    final var references = referenceIndex.getReferencesTo(variable);
    return references.stream()
      .sorted((o1, o2) -> compare(o1.getSelectionRange(), o2.getSelectionRange()))
      .collect(Collectors.toList());
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

  private boolean noneWritingToDefOrSelfAssign(Reference defRef, Reference nextRef) {
    final var defNode = Trees.findNodeContainsPosition(documentContext.getAst(),
      defRef.getSelectionRange().getStart());
    final var assignment = defNode
      .map(TerminalNode::getParent)
      .filter(BSLParser.LValueContext.class::isInstance)
      .map(BSLParser.LValueContext.class::cast)
      .map(context -> Trees.getRootParent(context, ROOTS_OF_DEF_OR_REFS))
      .filter(rootContext -> rootContext.getRuleIndex() == RULE_ASSIGNMENT);
    if (assignment.isEmpty()) {
      return true;
    }

    final var refContext = Trees.findNodeContainsPosition(assignment.get(), nextRef.getSelectionRange().getStart())
      .map(TerminalNode::getParent);
    if (refContext.isEmpty()){
      return true;
    }
    return isVarNameOnlyIntoExpression(assignment.get(), refContext);
  }

  private static boolean isVarNameOnlyIntoExpression(BSLParserRuleContext assignment, Optional<RuleNode> refContext) {
    return refContext
      .filter(BSLParser.ComplexIdentifierContext.class::isInstance)
      .map(BSLParser.ComplexIdentifierContext.class::cast)
      .filter(node -> node.getChildCount() == 1)
      .map(RuleNode::getParent)
      .filter(node -> node.getChildCount() == 1)
      .filter(BSLParser.MemberContext.class::isInstance)
      .map(RuleNode::getParent)
      .filter(node -> node.getChildCount() == 1)
      .filter(BSLParser.ExpressionContext.class::isInstance)
      .map(RuleNode::getParent)
      .filter(ruleNode -> ruleNode == assignment)
      .isPresent();
  }

  private void fireIssue(VariableSymbol variable, List<Reference> references) {
    var refsForIssue = references.stream()
      .map(reference -> RelatedInformation.create(
        documentContext.getUri(),
        reference.getSelectionRange(),
        "+1"
      )).collect(Collectors.toList());
    var resultRefs = new ArrayList<DiagnosticRelatedInformation>();
    resultRefs.add(RelatedInformation.create(
      documentContext.getUri(),
      variable.getVariableNameRange(),
      "0"));
    resultRefs.addAll(refsForIssue);

    diagnosticStorage.addDiagnostic(references.get(0).getSelectionRange(), info.getMessage(variable.getName()), resultRefs);
  }
}
