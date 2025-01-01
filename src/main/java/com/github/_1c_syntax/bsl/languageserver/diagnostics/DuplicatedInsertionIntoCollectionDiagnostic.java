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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.AssignmentContext;
import com.github._1c_syntax.bsl.parser.BSLParser.CallParamContext;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.BADPRACTICE
  }
)
public class DuplicatedInsertionIntoCollectionDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern INSERT_ADD_METHOD_PATTERN =
    CaseInsensitivePattern.compile("вставить|добавить|insert|add");
  private static final Pattern INSERT_METHOD_PATTERN = CaseInsensitivePattern.compile("вставить|insert");
  private static final Pattern IGNORED_BSL_VALUES_PATTERN = CaseInsensitivePattern.compile(
    "неопределено|undefined|0|символы\\.[\\wа-яё]+|chars\\.[\\wа-яё]+");

  private static final List<Integer> BREAKERS_INDEXES = Arrays.asList(BSLParser.RULE_returnStatement,
    BSLParser.RULE_breakStatement, BSLParser.RULE_continueStatement, BSLParser.RULE_raiseStatement);
  private static final List<Integer> BREAKERS_ROOTS = Arrays.asList(BSLParser.RULE_forEachStatement,
    BSLParser.RULE_forStatement, BSLParser.RULE_whileStatement, BSLParser.RULE_tryStatement);

  private static final int LENGTH_OF_EMPTY_STRING_WITH_QUOTES = 2;
  private static final boolean IS_ALLOWED_METHOD_ADD = true;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + IS_ALLOWED_METHOD_ADD
  )
  private boolean isAllowedMethodADD = IS_ALLOWED_METHOD_ADD;
  private Pattern methodPattern = INSERT_ADD_METHOD_PATTERN;

  // ленивое вычисление всех полей, только в нужный момент
  private BSLParser.CodeBlockContext codeBlock;
  private Range blockRange;
  private List<AssignmentContext> blockAssignments;
  private List<BSLParserRuleContext> blockBreakers;
  private List<CallParamContext> blockCallParams;
  private List<String> firstParamInnerIdentifiers;

  @Value
  private static class GroupingData {
    BSLParser.CallStatementContext callStatement;
    String collectionName;
    String collectionNameWithDot;
    String methodName;
    String firstParamName;
    String firstParamNameWithDot;
    CallParamContext firstParamContext;
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    super.configure(configuration);

    if (!isAllowedMethodADD) {
      methodPattern = INSERT_METHOD_PATTERN;
    }
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext codeBlock) {
    this.codeBlock = codeBlock;
    final var possibleDuplicateStatements = getPossibleDuplicates();

    if (!possibleDuplicateStatements.isEmpty()) {
      blockRange = Ranges.create(codeBlock);
      explorePossibleDuplicateStatements(possibleDuplicateStatements)
        .forEach(this::fireIssue);
    }
    clearCodeBlockFields();
    return super.visitCodeBlock(codeBlock);
  }

  private List<GroupingData> getPossibleDuplicates() {
    return codeBlock.statement().stream()
      .map(BSLParser.StatementContext::callStatement)
      .filter(Objects::nonNull)
      .filter(callStatement -> callStatement.accessCall() != null)
      .map(callStatement -> groupingCalls(callStatement, callStatement.accessCall()))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private @Nullable GroupingData groupingCalls(BSLParser.CallStatementContext callStatement,
                                               BSLParser.AccessCallContext accessCallContext) {
    final var methodCallContext = accessCallContext.methodCall();
    final var callParams = methodCallContext.doCall().callParamList().callParam();
    final var firstParamContext = callParams.get(0);
    if (firstParamContext.getChildCount() == 0) {
      return null;
    }
    final var methodName = methodCallContext.methodName().getText();
    if (!isAppropriateMethodCall(methodName)) {
      return null;
    }
    var firstParam = firstParamContext.getText();
    if (isBlankBSLString(firstParam) || isIgnoredBSLValues(firstParam)) {
      return null;
    }
    final TerminalNode identifierContext;
    final String parens;
    if (callStatement.IDENTIFIER() != null) {
      identifierContext = callStatement.IDENTIFIER();
      parens = "";
    } else {
      identifierContext = callStatement.globalMethodCall().methodName().IDENTIFIER();
      parens = "()";
    }
    final var collectionName = getFullIdentifier(identifierContext.getText().concat(parens), callStatement.modifier());

    return new GroupingData(callStatement, collectionName, collectionName.concat("."), methodName,
      firstParam, firstParam.concat("."), firstParamContext);
  }

  private boolean isAppropriateMethodCall(String methodName) {
    return methodPattern.matcher(methodName).matches();
  }

  private static boolean isBlankBSLString(String text) {
    final var length = text.length();

    return length >= LENGTH_OF_EMPTY_STRING_WITH_QUOTES && text.charAt(0) == '"' && text.charAt(length - 1) == '"'
      && text.substring(1, length - 1).isBlank();
  }

  private static boolean isIgnoredBSLValues(String text) {
    return IGNORED_BSL_VALUES_PATTERN.matcher(text).matches();
  }

  private Stream<List<GroupingData>> explorePossibleDuplicateStatements(List<GroupingData> statements) {
    final var mapOfMapsByIdentifier = statements.stream()
      .collect(Collectors.groupingBy(
        GroupingData::getCollectionName,
        CaseInsensitiveMap::new,
        Collectors.groupingBy(
          GroupingData::getMethodName,
          CaseInsensitiveMap::new,
          Collectors.groupingBy(
            GroupingData::getFirstParamName,
            CaseInsensitiveMap::new,
            Collectors.mapping(groupingData -> groupingData, Collectors.toList()))
        )
      ));
    return mapOfMapsByIdentifier.values().stream()
      .flatMap(mapByMethod -> mapByMethod.values().stream())
      .flatMap(mapByFirstParam -> mapByFirstParam.values().stream())
      .filter(duplicates -> duplicates.size() > 1)
      .map(this::excludeValidChanges)
      .filter(duplicates -> duplicates.size() > 1);
  }

  private List<GroupingData> excludeValidChanges(List<GroupingData> duplicates) {
    var result = new ArrayList<GroupingData>();
    for (var i = 0; i < duplicates.size(); i++) {
      if (!excludeValidElements(duplicates, i, result)) {
        break;
      }
    }
    firstParamInnerIdentifiers = null;
    return result;
  }

  private boolean excludeValidElements(List<GroupingData> duplicates, int currIndex,
                                       List<GroupingData> listForIssue) {
    if (duplicates.size() - currIndex <= 1) {
      return false;
    }
    final var first = duplicates.get(currIndex);
    var alreadyAdd = !listForIssue.isEmpty() && listForIssue.get(listForIssue.size() - 1) == first;
    for (int i = currIndex + 1; i < duplicates.size(); i++) {
      final var next = duplicates.get(i);
      if (hasValidChange(first, next)) {
        break;// последующие элементы нет смысла проверять, их нужно исключать
      }
      if (!alreadyAdd) {
        alreadyAdd = true;
        listForIssue.add(first);
      }
      listForIssue.add(next);
    }
    return true;
  }

  private boolean hasValidChange(GroupingData first, GroupingData next) {
    final var border = Ranges.create(first.callStatement.getStop(), next.callStatement.getStart());
    return hasAssignBetweenCalls(first, border)
      || hasBreakersBetweenCalls(border)
      || usedAsFunctionParamsBetweenCalls(border, first);
  }

  private boolean hasAssignBetweenCalls(GroupingData groupingData, Range border) {
    return getAssignments().stream()
      .filter(assignmentContext -> Ranges.containsRange(border, Ranges.create(assignmentContext)))
      .map(assignmentContext -> assignmentContext.lValue().getText())
      .anyMatch(assignText -> usedIdentifiers(assignText, groupingData));
  }

  private boolean usedIdentifiers(String expression, GroupingData groupingData) {
    final var expressionWithDot = expression.concat(".");
    if (startWithIgnoreCase(groupingData.collectionNameWithDot, expressionWithDot)) {
      return true;
    }
    return getAllInnerIdentifiersWithDot(groupingData.firstParamContext).stream()
      .anyMatch(identifierWithDot ->
        startWithIgnoreCase(identifierWithDot, expressionWithDot)
          || startWithIgnoreCase(expressionWithDot, identifierWithDot)
      );
  }

  private static boolean startWithIgnoreCase(String identifier, String textWithDot) {
    return identifier.length() >= textWithDot.length()
      && identifier.substring(0, textWithDot.length()).equalsIgnoreCase(textWithDot);
  }

  private boolean hasBreakersBetweenCalls(Range border) {
    return getBreakers().stream()
      .filter(bslParserRuleContext -> Ranges.containsRange(border, Ranges.create(bslParserRuleContext)))
      .anyMatch(this::hasBreakerIntoCodeBlock);
  }

  private boolean hasBreakerIntoCodeBlock(BSLParserRuleContext breakerContext) {
    if (breakerContext.getRuleIndex() == BSLParser.RULE_returnStatement) {
      return true;
    }
    final var rootParent = Trees.getRootParent(breakerContext, BREAKERS_ROOTS);
    if (rootParent == null) {
      return true; // сюда должны попасть, только если модуль не по грамматике, но иначе ругань на возможный null
    }
    return !Ranges.containsRange(blockRange, Ranges.create(rootParent));
  }

  private boolean usedAsFunctionParamsBetweenCalls(Range border, GroupingData groupingData) {
    return getCallParams().stream()
      .filter(callParamContext -> Ranges.containsRange(border, Ranges.create(callParamContext)))
      .anyMatch(callParamContext -> usedAsFunctionParams(callParamContext, groupingData));
  }

  private boolean usedAsFunctionParams(CallParamContext callParamContext, GroupingData groupingData) {
    return Optional.of(callParamContext)
      .map(CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .filter(memberContexts -> usedIdentifiers(memberContexts, groupingData))
      .isPresent();
  }

  private boolean usedIdentifiers(List<? extends BSLParser.MemberContext> memberContexts, GroupingData groupingData) {
    return memberContexts.stream()
      .map(BSLParser.MemberContext::complexIdentifier)
      .filter(Objects::nonNull)
      .filter(complexIdentifierContext -> complexIdentifierContext.IDENTIFIER() != null)
      .map(BSLParserRuleContext::getText)
      .anyMatch(identifier -> usedIdentifiers(identifier, groupingData));
  }

  private void fireIssue(List<GroupingData> duplicates) {
    final var dataForIssue = duplicates.get(1);
    final var relatedInformationList = duplicates.stream()
      .map(GroupingData::getCallStatement)
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(context),
        "+1"
      )).collect(Collectors.toList());
    final var message = info.getMessage(dataForIssue.firstParamName, dataForIssue.collectionName);
    diagnosticStorage.addDiagnostic(dataForIssue.callStatement, message, relatedInformationList);
  }

  private List<AssignmentContext> getAssignments() {
    if (blockAssignments == null) {
      blockAssignments = Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_assignment).stream()
        .map(AssignmentContext.class::cast)
        .collect(Collectors.toUnmodifiableList());
    }
    return blockAssignments;
  }

  private List<BSLParserRuleContext> getBreakers() {
    if (blockBreakers == null) {
      blockBreakers = Trees.findAllRuleNodes(codeBlock, BREAKERS_INDEXES).stream()
        .map(BSLParserRuleContext.class::cast)
        .collect(Collectors.toUnmodifiableList());
    }
    return blockBreakers;
  }

  private List<CallParamContext> getCallParams() {
    if (blockCallParams == null) {
      blockCallParams = Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_callParam).stream()
        .map(CallParamContext.class::cast)
        .collect(Collectors.toUnmodifiableList());
    }
    return blockCallParams;
  }

  private List<String> getAllInnerIdentifiersWithDot(CallParamContext param) {
    if (firstParamInnerIdentifiers == null) {
      final var identifiers = Trees.findAllRuleNodes(param, BSLParser.RULE_complexIdentifier).stream()
        .map(BSLParser.ComplexIdentifierContext.class::cast)
        .filter(complexIdentifierContext -> complexIdentifierContext.IDENTIFIER() != null)
        .toList();
      final var reducedIdentifiers = new ArrayList<String>();
      for (BSLParser.ComplexIdentifierContext identifier : identifiers) {
        final List<? extends BSLParser.ModifierContext> modifiers = identifier.modifier();
        final var firstIdentifier = identifier.IDENTIFIER().getText();
        var fullIdentifier = getFullIdentifier(firstIdentifier, modifiers);
        reducedIdentifiers.add(fullIdentifier);

        var reducedIdentifier = firstIdentifier;
        for (var modifier : modifiers) {
          var text = modifier.getText();
          reducedIdentifier = reducedIdentifier.concat(".").concat(text);
          reducedIdentifiers.add(reducedIdentifier);
        }
      }
      firstParamInnerIdentifiers = reducedIdentifiers;
    }
    return firstParamInnerIdentifiers;
  }

  private void clearCodeBlockFields() {
    codeBlock = null;
    blockRange = null;
    blockAssignments = null;
    blockBreakers = null;
    blockCallParams = null;
  }

  private static String getFullIdentifier(String firstIdentifier, List<? extends BSLParser.ModifierContext> modifiers) {
    return modifiers.stream()
      .map(BSLParserRuleContext::getText)
      .reduce(firstIdentifier, (x, y) -> x.concat(".").concat(y))
      .replace("..", ".");
  }
}
