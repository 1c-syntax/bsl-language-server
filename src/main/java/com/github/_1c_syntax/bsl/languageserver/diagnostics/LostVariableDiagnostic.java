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

import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
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
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNPREDICTABLE,
    DiagnosticTag.ERROR
  }

)

@RequiredArgsConstructor
public class LostVariableDiagnostic extends AbstractVisitorDiagnostic {

  private static final Set<VariableKind> CHECKING_VARIABLE_KINDS = EnumSet.of(
//    VariableKind.MODULE,
//    VariableKind.LOCAL, // TODO учесть разные типы переменных
    VariableKind.DYNAMIC
  );
  private final ReferenceIndex referenceIndex;

  @Value
  private static class VarData implements Comparable<VarData> {
    String name;
    Range defRange;
    Range rewriteRange;
    List<Reference> references;

    @Override
    public int compareTo(@NotNull LostVariableDiagnostic.VarData o) {
      return compare(this.getDefRange(), o.getDefRange());
    }

  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    documentContext.getSymbolTree().getVariables().stream()
      .filter(variable -> CHECKING_VARIABLE_KINDS.contains(variable.getKind()))
      .flatMap(variable -> getVarData(variable).stream())
      .filter(this::isLostVariable)
      .forEach(this::fireIssue);

    return defaultResult();
  }

  private List<VarData> getVarData(VariableSymbol variable) {
    List<Reference> allReferences = getSortedReferencesByLocation(variable);
    if (allReferences.isEmpty()) {
      return Collections.emptyList();
    }
    return getConsecutiveDefinitions(variable, allReferences);
  }

  private List<Reference> getSortedReferencesByLocation(VariableSymbol variable) {
    final var references = referenceIndex.getReferencesTo(variable);
    return references.stream()
      .sorted((o1, o2) -> compare(o1.getSelectionRange(), o2.getSelectionRange()))
      .collect(Collectors.toList());
  }

  private List<VarData> getConsecutiveDefinitions(VariableSymbol variable, List<Reference> allReferences) {
    List<VarData> result = new ArrayList<>();
    Reference prev = null;
    if (allReferences.get(0).getOccurrenceType() == OccurrenceType.DEFINITION) {
      prev = allReferences.get(0);

      var references = allReferences.subList(1, allReferences.size());
      var varData = new VarData(variable.getName(), variable.getVariableNameRange(),
        allReferences.get(0).getSelectionRange(), references);
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
            next.getSelectionRange(), references);
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
    // TODO ? быстрее найти сначала метод в дереве, а потом уже переменную в дерере метода?
    //  чтобы постоянно не искать по всему дереву
    final var defNode = Trees.findNodeContainsPosition(documentContext.getAst(),
      varData.getDefRange().getStart());
    if (defNode.isEmpty()) {
      return false; // вдруг каким-то чудом все-таки не найдем )
    }
    var defCodeBlock = getCodeBlock(defNode);
    final var rewriteNodeInsideDefCodeBlock = defCodeBlock
      .flatMap(context -> Trees.findNodeContainsPosition(context,
        varData.rewriteRange.getStart()));
    if (rewriteNodeInsideDefCodeBlock.isEmpty()) {
      return false;
    }
    var rewriteCodeBlock = getCodeBlock(rewriteNodeInsideDefCodeBlock);
    if (defCodeBlock.get() != rewriteCodeBlock.get() && varData.references.isEmpty()) {
      return false;
    }
    return defCodeBlock.get() == rewriteCodeBlock.get()
      || rewriteCodeBlock
        .filter(codeBlock -> hasReferenceOutsideRewriteBlock(varData.references, codeBlock))
        .isEmpty();
  }

  private static Optional<BSLParser.CodeBlockContext> getCodeBlock(Optional<TerminalNode> defNode) {
    return defNode
      .map(TerminalNode::getParent)
      .map(BSLParserRuleContext.class::cast)
      .map(node -> Trees.getRootParent(node, BSLParser.RULE_codeBlock))
      .filter(BSLParser.CodeBlockContext.class::isInstance)
      .map(BSLParser.CodeBlockContext.class::cast);
  }

  private static boolean hasReferenceOutsideRewriteBlock(List<Reference> references, BSLParserRuleContext codeBlock) {
    return references.stream()
      .map(Reference::getSelectionRange)
      .anyMatch(range -> Trees.findNodeContainsPosition(codeBlock, range.getStart())
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
    diagnosticStorage.addDiagnostic(varData.getDefRange(), message);
//    diagnosticStorage.addDiagnostic(varData.getDefRange(), message, relatedInformationList);
  }
}
