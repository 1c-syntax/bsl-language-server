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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.eclipse.lsp4j.Range;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.BADPRACTICE
  }

)
public class DuplicatedInsertionIntoCollectionDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern ADD_METHOD_PATTERN = CaseInsensitivePattern.compile("добавить|вставить|add|insert");
  private static final Pattern IGNORED_BSL_VALUES_PATTERN = CaseInsensitivePattern.compile(
    "неопределено|undefined|0|символы\\.[\\wа-яё]+|chars\\.[\\wа-яё]+");

  private static final List<Integer> BREAKERS_INDEXES = Arrays.asList(BSLParser.RULE_returnStatement,
    BSLParser.RULE_breakStatement, BSLParser.RULE_continueStatement, BSLParser.RULE_raiseStatement);
  private static final List<Integer> BREAKERS_ROOTS = Arrays.asList(BSLParser.RULE_subCodeBlock,
    BSLParser.RULE_forEachStatement, BSLParser.RULE_forStatement, BSLParser.RULE_whileStatement,
    BSLParser.RULE_tryStatement);

  public static final int LENGTH_OF_EMPTY_STRING_WITH_QUOTES = 2;

  private Range blockRange;
  private List<BSLParser.AssignmentContext> blockAssignments;
  private List<BSLParserRuleContext> blockBreakers;
  private List<BSLParser.CallParamContext> blockCallParams;
  private final List<List<String>> firstParamComplexIdentifiersStorage = new ArrayList<>();

  @Value
  private static class GroupingData {
    BSLParser.CallStatementContext callStatement;
    String collectionName;
    String methodName;
    String firstParamName;
    String firstParamNameWithDot;
    BSLParser.CallParamContext firstParamContext;

}

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext codeBlock) {
    final var statements = codeBlock.statement().stream()
      .map(BSLParser.StatementContext::callStatement)
      .filter(Objects::nonNull)
      .filter(callStatement -> callStatement.IDENTIFIER() != null)
      .filter(callStatement -> callStatement.accessCall() != null)
      .map(callStatement -> groupingCalls(callStatement, callStatement.accessCall()))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    if (!statements.isEmpty()) {
      exploreStatements(codeBlock, statements);

      blockRange = null;
      blockAssignments = null;
      blockBreakers = null;
      blockCallParams = null;
    }
    return super.visitCodeBlock(codeBlock);
  }

  private @Nullable
  GroupingData groupingCalls(BSLParser.CallStatementContext callStatement, BSLParser.AccessCallContext accessCallContext) {
    final var methodCallContext = accessCallContext.methodCall();
    if (methodCallContext == null) {
      return null;
    }
    final var callParams = methodCallContext.doCall().callParamList().callParam();
    if (callParams.isEmpty()) {
      return null;
    }
    final BSLParser.CallParamContext firstParamContext = callParams.get(0);
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

    final var fullIdentifier = getFullIdentifier(callStatement.IDENTIFIER().getText(), callStatement.modifier());

    return new GroupingData(callStatement, fullIdentifier, methodName, firstParam, firstParam.concat("."),
      firstParamContext);
  }

  private static boolean isAppropriateMethodCall(String methodName) {
    return ADD_METHOD_PATTERN.matcher(methodName).matches();
  }

  private static boolean isBlankBSLString(String text) {
    final var length = text.length();

    return length >= LENGTH_OF_EMPTY_STRING_WITH_QUOTES && text.charAt(0) == '"' && text.charAt(length - 1) == '"'
      && text.substring(1, length - 1).isBlank();
  }

  private static boolean isIgnoredBSLValues(String text) {
    return IGNORED_BSL_VALUES_PATTERN.matcher(text).matches();
  }

  private void exploreStatements(BSLParser.CodeBlockContext codeBlock, List<GroupingData> statements) {
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
    mapOfMapsByIdentifier.values().stream()
      .flatMap(mapByMethod -> mapByMethod.values().stream())
      .flatMap(mapByFirstParam -> mapByFirstParam.values().stream())
      .filter(listOfDuplicatedData -> listOfDuplicatedData.size() > 1)
      .map(listOfDuplicatedData -> excludeValidChanges(listOfDuplicatedData, codeBlock))
      .filter(list -> list.size() > 1)
      .forEach(this::fireIssue);
  }

  private List<GroupingData> excludeValidChanges(List<GroupingData> listOfDuplicatedData, BSLParser.CodeBlockContext codeBlock) {

    var result = new ArrayList<GroupingData>();
    for (var i = 0; i < listOfDuplicatedData.size(); i++) {
      if (!excludeValidElements(listOfDuplicatedData, i, codeBlock, result)) {
        break;
      }
    }
    firstParamComplexIdentifiersStorage.clear();
    return result;
  }

  private boolean excludeValidElements(List<GroupingData> listOfDuplicatedData, int currIndex,
                                       BSLParser.CodeBlockContext codeBlock, List<GroupingData> listForIssue) {
    if (listOfDuplicatedData.size() - currIndex <= 1) {
      return false;
    }
    final var first = listOfDuplicatedData.get(currIndex);
    var alreadyAdd = false;
    for (int i = currIndex + 1; i < listOfDuplicatedData.size(); i++) {
      final var next = listOfDuplicatedData.get(i);
      if (hasValidChange(first, next, codeBlock)) {
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

  private boolean hasValidChange(GroupingData groupingData, GroupingData groupingData1, BSLParser.CodeBlockContext codeBlock) {
    final var range = Ranges.create(groupingData.callStatement, groupingData1.callStatement);
    if (hasAssignBetweenCalls(groupingData, range, codeBlock)
        || hasBreakersBetweenCalls(range, codeBlock)) {
      return true;
    }
    return usedAsFunctionParamsBetweenCalls(range, codeBlock, groupingData);
  }

  private boolean hasAssignBetweenCalls(GroupingData groupingData, Range range, BSLParser.CodeBlockContext codeBlock) {
    return getAssignments(codeBlock).stream()
      .filter(assignmentContext -> Ranges.containsRange(range, Ranges.create(assignmentContext)))
      .anyMatch(assignmentContext -> hasValidAssign(assignmentContext, groupingData));
  }

  private boolean hasValidAssign(BSLParser.AssignmentContext assignmentContext, GroupingData groupingData) {
    final var assignText = assignmentContext.lValue().getText();
    if (assignText.equalsIgnoreCase(groupingData.collectionName)
      || assignText.equalsIgnoreCase(groupingData.firstParamName)) {
      return true;
    }
    final var assignTextWithDot = assignText.concat(".");
    if (startWithIgnoreCase(groupingData.collectionName, assignTextWithDot)
      || startWithIgnoreCase(groupingData.firstParamName, assignTextWithDot)
      || startWithIgnoreCase(assignTextWithDot, groupingData.firstParamNameWithDot)
    ){
      return true;
    }
    final List<String> complexIdentifierContexts = getFirstParamComplexIdentifiers(groupingData.firstParamContext);
    return complexIdentifierContexts.stream()
      .anyMatch(identifier -> assignText.equalsIgnoreCase(identifier)
        || startWithIgnoreCase(identifier, assignTextWithDot)
        || startWithIgnoreCase(assignTextWithDot, identifier.concat(".")));
  }

  private boolean hasBreakersBetweenCalls(Range range, BSLParser.CodeBlockContext codeBlock) {
    return getBreakers(codeBlock).stream()
      .filter(bslParserRuleContext -> Ranges.containsRange(range, Ranges.create(bslParserRuleContext)))
      .anyMatch(breakerContext -> hasBreakerFromCodeBlock(breakerContext, codeBlock));
  }

  private boolean hasBreakerFromCodeBlock(BSLParserRuleContext breakerContext, BSLParser.CodeBlockContext codeBlock) {
    if (breakerContext.getRuleIndex() == BSLParser.RULE_returnStatement) {
      return true;
    }
    final var rootParent = Trees.getRootParent(breakerContext, BREAKERS_ROOTS);
    if (rootParent == null || rootParent.getRuleIndex() == BSLParser.RULE_subCodeBlock) {
      return true;
    }
    return !Ranges.containsRange(getBlockRange(codeBlock), Ranges.create(rootParent));
  }

  private boolean usedAsFunctionParamsBetweenCalls(Range range, BSLParser.CodeBlockContext codeBlock, GroupingData groupingData) {
    return getCallParams(codeBlock).stream()
      .filter(callParamContext -> Ranges.containsRange(range, Ranges.create(callParamContext)))
      .anyMatch(callParamContext -> usedAsFunctionParams(callParamContext, groupingData));
  }

  private static boolean usedAsFunctionParams(BSLParser.CallParamContext callParamContext, GroupingData groupingData) {
    return Optional.of(callParamContext)
      .map(BSLParser.CallParamContext::expression)
      .filter(expression -> !expression.member().isEmpty())
      .map(BSLParser.ExpressionContext::member)
      .filter(memberContexts -> memberContexts.stream()
        .map(BSLParser.MemberContext::complexIdentifier)
        .filter(Objects::nonNull)
        .anyMatch(complexIdentifierContext -> similarIdentifier(complexIdentifierContext, groupingData.collectionName)))
      .isPresent();
  }

  private static boolean similarIdentifier(BSLParser.ComplexIdentifierContext complexIdentifierContext, String collectionName) {
    return startWithIgnoreCase(collectionName, complexIdentifierContext.getText());
  }

  private void fireIssue(List<GroupingData> listOfDuplicatedData) {
    final var dataForIssue = listOfDuplicatedData.get(1);
    final var relatedInformationList = listOfDuplicatedData.stream()
      .map(GroupingData::getCallStatement)
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(context),
        "+1"
      )).collect(Collectors.toList());
    final var message = info.getMessage(dataForIssue.firstParamName, dataForIssue.collectionName);
    diagnosticStorage.addDiagnostic(dataForIssue.callStatement, message, relatedInformationList);
  }

  private Range getBlockRange(BSLParser.CodeBlockContext codeBlock) {
    if (blockRange == null) {
      blockRange = Ranges.create(codeBlock);
    }
    return blockRange;
  }

  private List<BSLParser.AssignmentContext> getAssignments(BSLParser.CodeBlockContext codeBlock) {
    if (blockAssignments == null) {
      blockAssignments = Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_assignment).stream()
        .map(BSLParser.AssignmentContext.class::cast)
        .collect(Collectors.toUnmodifiableList());
    }
    return blockAssignments;
  }

  private List<BSLParserRuleContext> getBreakers(BSLParser.CodeBlockContext codeBlock) {
    if (blockBreakers == null) {
      blockBreakers = Trees.findAllRuleNodes(codeBlock, BREAKERS_INDEXES).stream()
        .map(BSLParserRuleContext.class::cast)
        .collect(Collectors.toUnmodifiableList());
    }
    return blockBreakers;
  }

  private List<BSLParser.CallParamContext> getCallParams(BSLParser.CodeBlockContext codeBlock) {
    if (blockCallParams == null) {
      blockCallParams = Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_callParam).stream()
        .map(BSLParser.CallParamContext.class::cast)
        .collect(Collectors.toUnmodifiableList());
    }
    return blockCallParams;
  }

  private List<String> getFirstParamComplexIdentifiers(BSLParser.CallParamContext param) {
    if (firstParamComplexIdentifiersStorage.isEmpty()){
      final var complexIdentifierContexts =
        Trees.findAllRuleNodes(param, BSLParser.RULE_complexIdentifier).stream()
          .map(BSLParser.ComplexIdentifierContext.class::cast)
          .filter(complexIdentifierContext -> complexIdentifierContext.IDENTIFIER() != null)
          .map(complexIdentifierContext ->
            getFullIdentifier(complexIdentifierContext.IDENTIFIER().getText(), complexIdentifierContext.modifier()))
          .collect(Collectors.toUnmodifiableList());
      firstParamComplexIdentifiersStorage.add(complexIdentifierContexts);
    }
    return firstParamComplexIdentifiersStorage.get(0);
  }

  private static String getFullIdentifier(String firstIdentifier, List<? extends BSLParser.ModifierContext> modifiers) {
    return modifiers.stream()
      .map(BSLParserRuleContext::getText)
      .reduce(firstIdentifier, (x, y) -> x.concat(".").concat(y))
      .replace("..", ".");
  }

  private static boolean startWithIgnoreCase(String identifier, String textWithDot) {
    return identifier.length() >= textWithDot.length()
      && identifier.substring(0, textWithDot.length()).equalsIgnoreCase(textWithDot);
  }
}
