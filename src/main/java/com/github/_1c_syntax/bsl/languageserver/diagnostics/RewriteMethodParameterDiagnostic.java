/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  private static final int COUNT_OF_PAIR_FOR_SELF_ASSIGN = 2;
  private final ReferenceIndex referenceIndex;

  @Override
  public void check() {
    documentContext.getSymbolTree().getMethods().stream()
      .flatMap(RewriteMethodParameterDiagnostic::getParametersByValue)
      .flatMap(pair -> getVariableByParameter(pair.getLeft(), pair.getRight()))
      .map(this::isOverwrited)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .forEach(variableSymbolReferenceListTriple -> fireIssue(variableSymbolReferenceListTriple.getLeft(),
        variableSymbolReferenceListTriple.getMiddle(),
        variableSymbolReferenceListTriple.getRight()));
  }

  private static Stream<Pair<MethodSymbol, ParameterDefinition>> getParametersByValue(MethodSymbol methodSymbol) {
    return methodSymbol.getParameters().stream()
      .filter(ParameterDefinition::isByValue)
      .map(parameterDefinition -> Pair.of(methodSymbol, parameterDefinition));
  }

  private static Stream<VariableSymbol> getVariableByParameter(MethodSymbol method,
                                                               ParameterDefinition parameterDefinition) {
    return method.getChildren().stream()
      // в будущем могут появиться и другие символы, подчиненные методам
      .filter(sourceDefinedSymbol -> sourceDefinedSymbol.getSymbolKind() == SymbolKind.Variable)
      .filter(variable -> parameterDefinition.getRange().getStart().equals(variable.getSelectionRange().getStart()))
      .filter(VariableSymbol.class::isInstance)
      .map(VariableSymbol.class::cast)
      .findFirst().stream();
  }

  private Optional<Triple<VariableSymbol, Reference, List<Reference>>> isOverwrited(VariableSymbol variable) {
    final var references = getSortedReferencesByLocation(variable);
    return isOverwrited(references)
      .map(defReference -> Triple.of(variable, defReference, references));
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

  private Optional<Reference> isOverwrited(List<Reference> references) {
    if (references.isEmpty() || references.get(0).getOccurrenceType() != OccurrenceType.DEFINITION) {
      return Optional.empty();
    }

    final var firstDefIntoAssign = references.get(0);
    if (references.size() > 1) {
      var refContextInsideDefAssign = getRefContextInsideDefAssign(firstDefIntoAssign, references.get(1));
      if (refContextInsideDefAssign.isPresent()) {
        if (isVarNameOnlyIntoExpression(refContextInsideDefAssign.get())
          && references.size() > COUNT_OF_PAIR_FOR_SELF_ASSIGN) {
          return isOverwrited(references.subList(COUNT_OF_PAIR_FOR_SELF_ASSIGN, references.size()));
        }
        return Optional.empty();
      }
    }
    return Optional.of(firstDefIntoAssign);
  }

  private Optional<RuleNode> getRefContextInsideDefAssign(Reference defRef, Reference nextRef) {
    final var defNode = Trees.findTerminalNodeContainsPosition(documentContext.getAst(),
      defRef.getSelectionRange().getStart());
    final var assignment = defNode
      .map(TerminalNode::getParent)
      .filter(BSLParser.LValueContext.class::isInstance)
      .map(ParseTree::getParent)
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .map(BSLParser.AssignmentContext.class::cast);

    return assignment.flatMap(assignContext ->
        Trees.findTerminalNodeContainsPosition(assignContext, nextRef.getSelectionRange().getStart()))
      .map(TerminalNode::getParent);
  }

  private static boolean isVarNameOnlyIntoExpression(RuleNode refContext) {
    return Optional.of(refContext)
      .filter(BSLParser.ComplexIdentifierContext.class::isInstance)
      .map(BSLParser.ComplexIdentifierContext.class::cast)
      .filter(node -> node.getChildCount() == 1)
      .map(RuleNode::getParent)
      .filter(BSLParser.MemberContext.class::isInstance)
      .map(ParseTree::getParent)
      .filter(expression -> expression.getChildCount() == 1)
      .filter(BSLParser.ExpressionContext.class::isInstance)
      .isPresent();
  }

  private void fireIssue(VariableSymbol variable, Reference nodeForIssue, List<Reference> references) {
    var refsForIssue = references.stream()
      .map(reference -> RelatedInformation.create(
        documentContext.getUri(),
        reference.getSelectionRange(),
        "+1"
      )).toList();
    var resultRefs = new ArrayList<DiagnosticRelatedInformation>();
    resultRefs.add(RelatedInformation.create(
      documentContext.getUri(),
      variable.getVariableNameRange(),
      "0"));
    resultRefs.addAll(refsForIssue);

    diagnosticStorage.addDiagnostic(nodeForIssue.getSelectionRange(), info.getMessage(variable.getName()), resultRefs);
  }
}
