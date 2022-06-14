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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.antlr.v4.runtime.tree.RuleNode;
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
public class LostVariableDiagnostic extends AbstractDiagnostic {

  private static final Set<VariableKind> CHECKING_VARIABLE_KINDS = EnumSet.of(
//    VariableKind.MODULE,
//    VariableKind.LOCAL, // TODO учесть разные типы переменных
    VariableKind.DYNAMIC
  );
  private final ReferenceIndex referenceIndex;

  // TODO нужна опция для возможности работы правила в модулях форм и модулях объектов, могут быть FP

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
  protected void check() {
    documentContext.getSymbolTree().getVariables().stream()
      .filter(variable -> CHECKING_VARIABLE_KINDS.contains(variable.getKind()))
      .flatMap(variable -> getVarData(variable).stream())
      .filter(this::isLostVariable)
      .forEach(this::fireIssue);
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
      varData.getDefRange().getStart())
      .map(TerminalNode::getParent)
      .orElseThrow();
//    if (defNode.isEmpty()) {
//      return false; // вдруг каким-то чудом все-таки не найдем )
//    }
//    var defNode = defNode.orElseThrow();
    var defCodeBlock = getCodeBlock(defNode);
    final var rewriteNodeInsideDefCodeBlockOpt = defCodeBlock
      .flatMap(context -> Trees.findNodeContainsPosition(context,
        varData.rewriteRange.getStart()))
      .map(TerminalNode::getParent);
    if (rewriteNodeInsideDefCodeBlockOpt.isEmpty()) {
      return false;
    }
    var rewriteNode = rewriteNodeInsideDefCodeBlockOpt.get();
    var rewriteCodeBlock = getCodeBlock(rewriteNode).orElseThrow();

    var insideOneBlock = defCodeBlock.get() == rewriteCodeBlock;
    if (!insideOneBlock) {
      if (varData.references.isEmpty()) {
        return false;
      }
      var rewriteStatement = getRootStatement(rewriteNode);
      if (Ranges.containsRange(Ranges.create(rewriteStatement), varData.references.get(0).getSelectionRange())) {
        return false;
      }
//      return true;
    }
    if (insideOneBlock){
      if (!varData.references.isEmpty()) {
        var rewriteStatement = getRootStatement(rewriteNode);
        if (Ranges.containsRange(Ranges.create(rewriteStatement), varData.references.get(0).getSelectionRange())) {
          return false;
        }
      }
//      //var defParentExpression = getParentExpression(defNode);
//      var rewriteParentExpression = getParentExpression(rewriteNode);
//      var noneSelfAssign = rewriteParentExpression.isEmpty();
//      if (noneSelfAssign){
//        return true;
//      }
//
//      var defParentStatement = getRootStatement(defNode);
//      var rewriteParentStatement = getRootStatement(rewriteParentExpression);
//      if (defParentStatement != rewriteParentStatement){
//        return true;
//      }
//      return !isVarNameOnlyIntoExpression(rewriteNode);
    }
    return insideOneBlock
      || !hasReferenceOutsideRewriteBlock(varData.references, rewriteCodeBlock);
//      return !hasReferenceOutsideRewriteBlock(varData.references, rewriteCodeBlock);
  }

  private static Optional<BSLParser.CodeBlockContext> getCodeBlock(RuleNode context) {
    return getRootNode(context, BSLParser.RULE_codeBlock, BSLParser.CodeBlockContext.class);
//    return Optional.of(context)
//      .map(BSLParserRuleContext.class::cast)
//      .map(node -> Trees.getRootParent(node, BSLParser.RULE_codeBlock))
//      .filter(BSLParser.CodeBlockContext.class::isInstance)
//      .map(BSLParser.CodeBlockContext.class::cast);
  }

  private static Optional<BSLParser.ExpressionContext> getParentExpression(RuleNode context) {
    return getRootNode(context, BSLParser.RULE_expression, BSLParser.ExpressionContext.class);
//    return Optional.of(context)
//      .map(BSLParserRuleContext.class::cast)
//      .map(node -> Trees.getRootParent(node, BSLParser.RULE_expression))
//      .filter(BSLParser.ExpressionContext.class::isInstance)
//      .map(BSLParser.ExpressionContext.class::cast)
//      .orElseThrow();// TODO падает на Комментарий = 10;Комментарий = 20; (важно, что нет пробела после 10;)
  }

  private static <T extends BSLParserRuleContext> Optional<T> getRootNode(RuleNode context, int index, Class<T> klass) {
    return Optional.of(context)
      .map(BSLParserRuleContext.class::cast)
      .map(node -> Trees.getRootParent(node, index))
      .filter(klass::isInstance)
      .map(klass::cast);
  }

//  private BSLParser.StatementContext getRootStatement(TerminalNode terminalNode) {
//    return getRootStatement(terminalNode.getParent());
//  }

  private static BSLParser.StatementContext getRootStatement(RuleNode node) {
    return getRootNode(node, BSLParser.RULE_statement, BSLParser.StatementContext.class)
//    return Optional.of(node)
//      .map(BSLParserRuleContext.class::cast)
//      .map(node1 -> Trees.getRootParent(node1, BSLParser.RULE_statement))
//      .filter(BSLParser.StatementContext.class::isInstance)
//      .map(BSLParser.StatementContext.class::cast)
      .orElseThrow();// TODO падает на Комментарий = 10;Комментарий = 20; (важно, что нет пробела после 10;)
  }

  private static boolean isVarNameOnlyIntoExpression(RuleNode context) {
    return Optional.of(context)
      .filter(BSLParser.ComplexIdentifierContext.class::isInstance)
      .map(BSLParser.ComplexIdentifierContext.class::cast)
      .filter(node -> node.getChildCount() == 1)
      .map(RuleNode::getParent)
      .filter(BSLParser.MemberContext.class::isInstance)
      .map(RuleNode::getParent)
      .filter(expression -> expression.getChildCount() == 1)
      .filter(BSLParser.ExpressionContext.class::isInstance)
      .isPresent();
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
