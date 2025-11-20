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

import java.util.Optional;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ParserRuleContext;

import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.bsl.Constructors;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

/**
 * Абстрактный базовый класс для диагностик магических значений (чисел и дат).
 * Содержит общую логику проверки использования значений в структурах и соответствиях.
 */
public abstract class AbstractMagicValueDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern INSERT_METHOD_PATTERN = CaseInsensitivePattern.compile(
    "вставить|insert"
  );

  private static final int MAX_PARENT_TRAVERSAL_DEPTH_FOR_CALL_STATEMENT = 10;
  private static final int MAX_PARENT_TRAVERSAL_DEPTH_FOR_VARIABLE_ASSIGNMENT = 50;
  private static final int MAX_PARENT_TRAVERSAL_DEPTH_FOR_CALL_PARAM = 5;

  /**
   * Получить ExpressionContext из узла AST.
   * Работает для разных типов узлов (NumericContext, ConstValueContext и т.д.).
   *
   * @param ctx узел AST
   * @return Optional с ExpressionContext, если найден
   */
  protected static Optional<BSLParser.ExpressionContext> getExpression(ParserRuleContext ctx) {
    return Optional.of(ctx)
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent)
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent)
      .filter(BSLParser.ExpressionContext.class::isInstance)
      .map(BSLParser.ExpressionContext.class::cast);
  }

  /**
   * Получить ExpressionContext из Optional узла.
   * Используется для ConstValueContext.
   *
   * @param constValue ConstValueContext
   * @return Optional с ExpressionContext, если найден
   */
  protected static Optional<BSLParser.ExpressionContext> getExpression(BSLParser.ConstValueContext constValue) {
    if (constValue == null) {
      return Optional.empty();
    }
    return Optional.of(constValue)
      .map(ParserRuleContext::getParent)
      .filter(context -> context.getChildCount() == 1)
      .map(ParserRuleContext::getParent)
      .filter(context -> context.getChildCount() == 1)
      .filter(BSLParser.ExpressionContext.class::isInstance)
      .map(BSLParser.ExpressionContext.class::cast);
  }

  /**
   * Проверяет, находится ли выражение внутри структуры или соответствия.
   * Это включает проверку:
   * - Второго параметра метода Вставить структуры или соответствия
   * - Параметров конструктора структуры (после первого строкового параметра)
   * - Прямого присваивания свойству структуры
   * - Второго параметра метода Вставить соответствия
   * - Первого параметра метода Вставить соответствия (ключ соответствия)
   *
   * @param expression выражение для проверки
   * @return true, если выражение находится внутри структуры или соответствия
   */
  protected boolean insideStructureOrCorrespondence(BSLParser.ExpressionContext expression) {    
    if (checkInsideStructureInsertOrAdd(expression)) {
      return true;
    }
    if (checkInsideStructureConstructor(expression)) {
      return true;
    }
    if (checkInsideStructurePropertyAssignment(expression)) {
      return true;
    }
    if (checkInsideCorrespondenceInsert(expression)) {
      return true;
    }
    return checkInsideCorrespondenceInsertFirstParam(expression);
  }

  /**
   * Проверяет, находится ли выражение во втором параметре метода Вставить структуры.
   *
   * @param expression выражение для проверки
   * @return true, если выражение находится во втором параметре метода Вставить структуры
   */
  private static boolean checkInsideStructureInsertOrAdd(BSLParser.ExpressionContext expression) {
    return expression.getParent() instanceof BSLParser.CallParamContext callParamContext
      && isSecondParameterOfStructureInsert(callParamContext);
  }

  /**
   * Проверяет, является ли CallParamContext вторым параметром метода Вставить структуры.
   *
   * @param callParam параметр вызова метода
   * @return true, если это второй параметр метода Вставить структуры
   */
  private static boolean isSecondParameterOfStructureInsert(BSLParser.CallParamContext callParam) {
    var callParamListOpt = getCallParamList(callParam);
    if (callParamListOpt.isEmpty()) {
      return false;
    }
    var callParamList = callParamListOpt.get();
    var callParams = callParamList.callParam();
    var paramIndex = callParams.indexOf(callParam);

    if (paramIndex != 1) {
      return false;
    }

    var variableType = getVariableTypeFromCallParamList(callParamList);
    if (variableType.isPresent()) {
      return !variableType.get();
    }

    return false;
  }

  /**
   * Находит MethodCallContext для данного DoCallContext.
   *
   * @param doCall контекст вызова метода
   * @return Optional с MethodCallContext или empty, если не найден
   */
  private static Optional<BSLParser.MethodCallContext> findMethodCall(BSLParser.DoCallContext doCall) {
    var parent = doCall.getParent();
    if (parent instanceof BSLParser.MethodCallContext methodCallContext) {
      return Optional.of(methodCallContext);
    }
    return Optional.empty();
  }

  /**
   * Находит CallStatementContext для данного MethodCallContext.
   *
   * @param methodCall контекст вызова метода
   * @return Optional с CallStatementContext или empty, если не найден
   */
  private static Optional<BSLParser.CallStatementContext> findCallStatement(BSLParser.MethodCallContext methodCall) {
    var current = methodCall.getParent();
    for (var i = 0; i < MAX_PARENT_TRAVERSAL_DEPTH_FOR_CALL_STATEMENT && current != null; i++) {
      if (current instanceof BSLParser.CallStatementContext callStatementContext) {
        return Optional.of(callStatementContext);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  /**
   * Безопасно получает CallParamListContext из CallParamContext.
   *
   * @param callParam контекст параметра вызова
   * @return Optional с CallParamListContext или empty, если родитель не является CallParamListContext
   */
  private static Optional<BSLParser.CallParamListContext> getCallParamList(BSLParser.CallParamContext callParam) {
    var parent = callParam.getParent();
    if (parent instanceof BSLParser.CallParamListContext callParamList) {
      return Optional.of(callParamList);
    }
    return Optional.empty();
  }

  /**
   * Безопасно получает DoCallContext из CallParamListContext.
   *
   * @param callParamList контекст списка параметров вызова
   * @return Optional с DoCallContext или empty, если родитель не является DoCallContext
   */
  private static Optional<BSLParser.DoCallContext> getDoCall(BSLParser.CallParamListContext callParamList) {
    var parent = callParamList.getParent();
    if (parent instanceof BSLParser.DoCallContext doCall) {
      return Optional.of(doCall);
    }
    return Optional.empty();
  }

  /**
   * Безопасно получает CallParamContext из ExpressionContext.
   *
   * @param expression контекст выражения
   * @return Optional с CallParamContext или empty, если родитель не является CallParamContext
   */
  private static Optional<BSLParser.CallParamContext> getCallParam(BSLParser.ExpressionContext expression) {
    var parent = expression.getParent();
    if (parent instanceof BSLParser.CallParamContext callParam) {
      return Optional.of(callParam);
    }
    return Optional.empty();
  }

  /**
   * Получает тип переменной из CallParamListContext через вызов метода Вставить.
   *
   * @param callParamList контекст списка параметров вызова
   * @return Optional с типом переменной или empty, если не удалось определить
   */
  private static Optional<Boolean> getVariableTypeFromCallParamList(BSLParser.CallParamListContext callParamList) {
    var doCallOpt = getDoCall(callParamList);
    if (doCallOpt.isEmpty()) {
      return Optional.empty();
    }
    var methodCallOpt = isInsertMethod(doCallOpt.get());
    if (methodCallOpt.isEmpty()) {
      return Optional.empty();
    }
    return getVariableTypeFromCallStatement(methodCallOpt.get());
  }

  /**
   * Проверяет, является ли метод методом "Вставить".
   *
   * @param doCall контекст вызова метода
   * @return Optional с MethodCallContext или empty, если метод не является "Вставить"
   */
  private static Optional<BSLParser.MethodCallContext> isInsertMethod(BSLParser.DoCallContext doCall) {
    var methodCallOpt = findMethodCall(doCall);
    if (methodCallOpt.isEmpty()) {
      return Optional.empty();
    }

    var methodCall = methodCallOpt.get();
    var methodName = methodCall.methodName().getText();
    if (!INSERT_METHOD_PATTERN.matcher(methodName).matches()) {
      return Optional.empty();
    }

    return Optional.of(methodCall);
  }

  /**
   * Получает тип переменной из CallStatement через MethodCallContext.
   *
   * @param methodCall контекст вызова метода
   * @return Optional с типом переменной или empty, если не удалось определить
   */
  private static Optional<Boolean> getVariableTypeFromCallStatement(BSLParser.MethodCallContext methodCall) {
    var callStatementOpt = findCallStatement(methodCall);
    if (callStatementOpt.isEmpty()) {
      return Optional.empty();
    }

    return getVariableTypeFromAST(callStatementOpt.get());
  }

  /**
   * Информация о параметре вызова метода.
   *
   * @param callParam контекст параметра вызова
   * @param paramIndex индекс параметра в списке параметров
   * @param callParamList контекст списка параметров вызова
   */
  private record CallParamInfo(
    BSLParser.CallParamContext callParam,
    int paramIndex,
    BSLParser.CallParamListContext callParamList
  ) {}

  /**
   * Получает CallParamContext и его индекс из ExpressionContext.
   *
   * @param expression выражение для проверки
   * @return Optional с информацией о параметре вызова или empty, если не найден
   */
  private static Optional<CallParamInfo> getCallParamInfo(BSLParser.ExpressionContext expression) {
    var callParamOpt = getCallParam(expression);
    if (callParamOpt.isEmpty()) {
      return Optional.empty();
    }
    var callParamContext = callParamOpt.get();
    var callParamListOpt = getCallParamList(callParamContext);
    if (callParamListOpt.isEmpty()) {
      return Optional.empty();
    }
    var callParamList = callParamListOpt.get();
    var callParams = callParamList.callParam();
    var paramIndex = callParams.indexOf(callParamContext);
    return Optional.of(new CallParamInfo(callParamContext, paramIndex, callParamList));
  }

  /**
   * Определяет тип переменной через статический анализ AST.
   * Ищет объявление переменной и проверяет тип через NewExpressionContext.
   *
   * @param callStatement контекст вызова метода с идентификатором переменной
   * @return Optional с типом: true - соответствие, false - структура, empty - неопределено
   */
  private static Optional<Boolean> getVariableTypeFromAST(BSLParser.CallStatementContext callStatement) {
    var identifier = callStatement.IDENTIFIER();
    var variableName = identifier.getText();
    var assignmentOpt = findVariableAssignment(callStatement, variableName);
    if (assignmentOpt.isEmpty() || assignmentOpt.get().expression() == null) {
      return Optional.empty();
    }
    
    var typeNameOpt = extractTypeNameFromExpression(assignmentOpt.get().expression());
    if (typeNameOpt.isEmpty()) {
      return Optional.empty();
    }

    return determineVariableType(typeNameOpt.get());
  }

  /**
   * Извлекает имя типа из ExpressionContext через цепочку member -> complexIdentifier -> newExpression.
   *
   * @param expression выражение для анализа
   * @return Optional с именем типа или empty, если не удалось извлечь
   */
  private static Optional<String> extractTypeNameFromExpression(BSLParser.ExpressionContext expression) {
    if (expression.member().isEmpty()) {
      return Optional.empty();
    }

    var firstMember = expression.member(0);
    if (firstMember == null || firstMember.complexIdentifier() == null) {
      return Optional.empty();
    }

    var complexIdentifier = firstMember.complexIdentifier();
    var newExpression = complexIdentifier.newExpression();
    if (newExpression == null) {
      return Optional.empty();
    }

    return Constructors.typeName(newExpression);
  }

  /**
   * Определяет тип переменной по имени типа.
   *
   * @param typeName имя типа
   * @return Optional с типом: true - соответствие, false - структура, empty - неопределено
   */
  private static Optional<Boolean> determineVariableType(String typeName) {
    if ("Соответствие".equalsIgnoreCase(typeName) || "Map".equalsIgnoreCase(typeName)) {
      return Optional.of(true);
    }
    if ("Структура".equalsIgnoreCase(typeName) || "Structure".equalsIgnoreCase(typeName)
      || "ФиксированнаяСтруктура".equalsIgnoreCase(typeName) || "FixedStructure".equalsIgnoreCase(typeName)) {
      return Optional.of(false);
    }
    return Optional.empty();
  }

  /**
   * Находит AssignmentContext для переменной с указанным именем.
   * Ищет в пределах того же блока кода (CodeBlockContext), поднимаясь по AST вверх.
   *
   * @param startNode начальный узел для поиска
   * @param variableName имя переменной
   * @return Optional с AssignmentContext или empty, если не найден
   */
  private static Optional<BSLParser.AssignmentContext> findVariableAssignment(ParserRuleContext startNode, String variableName) {
    var current = startNode.getParent();
    for (var i = 0; i < MAX_PARENT_TRAVERSAL_DEPTH_FOR_VARIABLE_ASSIGNMENT && current != null; i++) {
      if (current instanceof BSLParser.AssignmentContext assignmentContext
        && isAssignmentForVariable(assignmentContext, variableName)) {
        return Optional.of(assignmentContext);
      }
      current = current.getParent();
    }

    var codeBlock = Trees.getAncestorByRuleIndex(startNode, BSLParser.RULE_codeBlock);
    if (codeBlock instanceof BSLParser.CodeBlockContext codeBlockContext) {
      return findAssignmentInCodeBlock(codeBlockContext, startNode, variableName);
    }

    return Optional.empty();
  }

  /**
   * Находит AssignmentContext для переменной в пределах блока кода до указанного узла.
   *
   * @param codeBlock блок кода для поиска
   * @param beforeNode узел, до которого нужно искать
   * @param variableName имя переменной
   * @return Optional с AssignmentContext или empty, если не найден
   */
  private static Optional<BSLParser.AssignmentContext> findAssignmentInCodeBlock(
    BSLParser.CodeBlockContext codeBlock,
    ParserRuleContext beforeNode,
    String variableName
  ) {
    var beforeLine = beforeNode.getStart().getLine();
    var beforeChar = beforeNode.getStart().getCharPositionInLine();

    for (var statement : codeBlock.statement()) {
      if (statement == null) {
        continue;
      }

      var assignmentOpt = findAssignmentInStatement(statement, beforeLine, beforeChar, variableName);
      if (assignmentOpt.isPresent()) {
        return assignmentOpt;
      }
    }

    return Optional.empty();
  }

  /**
   * Находит AssignmentContext для переменной в statement до указанной позиции.
   *
   * @param statement statement для поиска
   * @param beforeLine номер строки, до которой нужно искать
   * @param beforeChar позиция символа, до которой нужно искать
   * @param variableName имя переменной
   * @return Optional с AssignmentContext или empty, если не найден
   */
  private static Optional<BSLParser.AssignmentContext> findAssignmentInStatement(
    BSLParser.StatementContext statement,
    int beforeLine,
    int beforeChar,
    String variableName
  ) {
    for (var i = 0; i < statement.getChildCount(); i++) {
      var child = statement.getChild(i);
      if (child instanceof BSLParser.AssignmentContext assignmentContext
        && isAssignmentBeforePosition(assignmentContext, beforeLine, beforeChar)
        && isAssignmentForVariable(assignmentContext, variableName)) {
        return Optional.of(assignmentContext);
      }
    }
    return Optional.empty();
  }

  /**
   * Проверяет, находится ли присваивание до указанной позиции.
   *
   * @param assignment контекст присваивания
   * @param beforeLine номер строки, до которой нужно искать
   * @param beforeChar позиция символа, до которой нужно искать
   * @return true, если присваивание находится до указанной позиции
   */
  private static boolean isAssignmentBeforePosition(
    BSLParser.AssignmentContext assignment,
    int beforeLine,
    int beforeChar
  ) {
    var assignmentLine = assignment.getStart().getLine();
    var assignmentChar = assignment.getStart().getCharPositionInLine();
    return assignmentLine < beforeLine || (assignmentLine == beforeLine && assignmentChar < beforeChar);
  }

  /**
   * Проверяет, является ли присваивание присваиванием указанной переменной.
   *
   * @param assignment контекст присваивания
   * @param variableName имя переменной
   * @return true, если это присваивание указанной переменной
   */
  private static boolean isAssignmentForVariable(
    BSLParser.AssignmentContext assignment,
    String variableName
  ) {
    var lValue = assignment.lValue();
    if (lValue == null) {
      return false;
    }
    var identifier = lValue.IDENTIFIER();
    return identifier != null && identifier.getText().equalsIgnoreCase(variableName);
  }

  /**
   * Проверяет, находится ли выражение в конструкторе структуры.
   * Параметры конструктора структуры: первый - строка с именами полей, остальные - значения.
   *
   * @param expression выражение для проверки
   * @return true, если выражение находится в конструкторе структуры
   */
  private static boolean checkInsideStructureConstructor(BSLParser.ExpressionContext expression) {
    var callParamContextOpt = findCallParamContext(expression);
    if (callParamContextOpt.isEmpty()) {
      return false;
    }

    var typeNameOpt = getStructureTypeName(callParamContextOpt.get());
    if (typeNameOpt.isEmpty()) {
      return false;
    }

    return isValidStructureConstructorParameter(callParamContextOpt.get(), typeNameOpt.get());
  }

  /**
   * Находит CallParamContext для выражения, поднимаясь по AST вверх.
   *
   * @param expression выражение для поиска
   * @return Optional с CallParamContext или empty, если не найден
   */
  private static Optional<BSLParser.CallParamContext> findCallParamContext(BSLParser.ExpressionContext expression) {
    var current = expression.getParent();
    for (var i = 0; i < MAX_PARENT_TRAVERSAL_DEPTH_FOR_CALL_PARAM && current != null; i++) {
      if (current instanceof BSLParser.CallParamContext paramContext) {
        return Optional.of(paramContext);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  /**
   * Получает typeName структуры из CallParamContext.
   *
   * @param callParamContext контекст параметра вызова
   * @return Optional с typeName структуры или empty, если не является конструктором структуры
   */
  private static Optional<BSLParser.TypeNameContext> getStructureTypeName(BSLParser.CallParamContext callParamContext) {
    var callParamListOpt = getCallParamList(callParamContext);
    if (callParamListOpt.isEmpty()) {
      return Optional.empty();
    }
    var doCallOpt = getDoCall(callParamListOpt.get());
    if (doCallOpt.isEmpty()) {
      return Optional.empty();
    }
    var newExpression = doCallOpt.get().getParent();
    if (!(newExpression instanceof BSLParser.NewExpressionContext)) {
      return Optional.empty();
    }

    var typeName = ((BSLParser.NewExpressionContext) newExpression).typeName();
    if (typeName == null || (!DiagnosticHelper.isStructureType(typeName)
      && !DiagnosticHelper.isFixedStructureType(typeName))) {
      return Optional.empty();
    }

    return Optional.of(typeName);
  }

  /**
   * Проверяет, является ли параметр валидным параметром конструктора структуры.
   *
   * @param callParamContext контекст параметра вызова
   * @param typeName тип структуры
   * @return true, если параметр валиден
   */
  private static boolean isValidStructureConstructorParameter(
    BSLParser.CallParamContext callParamContext,
    BSLParser.TypeNameContext typeName
  ) {
    var parent = callParamContext.getParent();
    if (!(parent instanceof BSLParser.CallParamListContext callParamList)) {
      return false;
    }
    var callParams = callParamList.callParam();
    
    var paramIndex = callParams.indexOf(callParamContext);
    if (paramIndex == 0) {
      return false;
    }
    
    if (DiagnosticHelper.isFixedStructureType(typeName)) {
      return true;
    }
    
    var firstParam = callParams.get(0);
    var tokens = firstParam.getTokens();
    if (tokens.isEmpty()) {
      return false;
    }
    return tokens.get(0).getType() == BSLParser.STRING;
  }

  /**
   * Проверяет, находится ли выражение в прямом присваивании свойству структуры.
   *
   * @param expression выражение для проверки
   * @return true, если выражение находится в прямом присваивании свойству структуры
   */
  private static boolean checkInsideStructurePropertyAssignment(BSLParser.ExpressionContext expression) {
    var assignmentOpt = findAssignment(expression);
    if (assignmentOpt.isEmpty()) {
      return false;
    }

    var lValue = assignmentOpt.get().lValue();

    return lValue != null
      && lValue.getChildCount() > 0
      && lValue.getChild(0) instanceof BSLParser.AccessPropertyContext;
  }

  /**
   * Находит AssignmentContext для данного ExpressionContext.
   *
   * @param expression выражение
   * @return Optional с AssignmentContext или empty, если не найден
   */
  private static Optional<BSLParser.AssignmentContext> findAssignment(BSLParser.ExpressionContext expression) {
    var parent = expression.getParent();
    if (parent instanceof BSLParser.AssignmentContext assignmentContext) {
      return Optional.of(assignmentContext);
    }
    var current = parent;
    for (var i = 0; i < MAX_PARENT_TRAVERSAL_DEPTH_FOR_CALL_PARAM && current != null; i++) {
      if (current instanceof BSLParser.AssignmentContext assignmentContext2) {
        return Optional.of(assignmentContext2);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  /**
   * Проверяет, находится ли выражение во втором параметре метода Вставить соответствия.
   * В соответствии ключ может быть любым типом, а значение - во втором параметре.
   *
   * @param expression выражение для проверки
   * @return true, если выражение находится во втором параметре метода Вставить соответствия
   */
  private static boolean checkInsideCorrespondenceInsert(BSLParser.ExpressionContext expression) {
    var callParamInfoOpt = getCallParamInfo(expression);
    if (callParamInfoOpt.isEmpty()) {
      return false;
    }

    var callParamInfo = callParamInfoOpt.get();
    if (callParamInfo.paramIndex() != 1) {
      return false;
    }

    var variableType = getVariableTypeFromCallParamList(callParamInfo.callParamList());
    if (variableType.isPresent()) {
      return variableType.get();
    }

    return false;
  }

  /**
   * Проверяет, находится ли выражение в первом параметре метода Вставить соответствия.
   * В соответствии ключ может быть любым типом, включая числа и даты.
   *
   * @param expression выражение для проверки
   * @return true, если выражение находится в первом параметре метода Вставить соответствия
   */
  private static boolean checkInsideCorrespondenceInsertFirstParam(BSLParser.ExpressionContext expression) {
    if (!isFirstParameterOfInsertMethod(expression)) {
      return false;
    }

    var callParamOpt = getCallParam(expression);
    if (callParamOpt.isEmpty()) {
      return false;
    }
    var callParamListOpt = getCallParamList(callParamOpt.get());
    if (callParamListOpt.isEmpty()) {
      return false;
    }
    var variableType = getVariableTypeFromCallParamList(callParamListOpt.get());
    if (variableType.isPresent()) {
      return variableType.get();
    }

    return false;
  }

  /**
   * Проверяет, является ли выражение первым параметром метода Вставить.
   *
   * @param expression выражение для проверки
   * @return true, если выражение является первым параметром метода Вставить
   */
  private static boolean isFirstParameterOfInsertMethod(BSLParser.ExpressionContext expression) {
    var callParamInfoOpt = getCallParamInfo(expression);
    if (callParamInfoOpt.isEmpty()) {
      return false;
    }

    var callParamInfo = callParamInfoOpt.get();
    if (callParamInfo.paramIndex() != 0) {
      return false;
    }

    var doCallOpt = getDoCall(callParamInfo.callParamList());
    if (doCallOpt.isEmpty()) {
      return false;
    }
    return isInsertMethod(doCallOpt.get()).isPresent();
  }

  /**
   * Проверяет, находится ли выражение в простом присваивании.
   *
   * @param expression выражение для проверки
   * @return true, если выражение находится в простом присваивании
   */
  protected static boolean insideSimpleAssignment(BSLParser.ExpressionContext expression) {
    return insideContext(expression, BSLParser.AssignmentContext.class);
  }

  /**
   * Проверяет, находится ли выражение в return statement.
   *
   * @param expression выражение для проверки
   * @return true, если выражение находится в return statement
   */
  protected static boolean insideReturnStatement(BSLParser.ExpressionContext expression) {
    return insideContext(expression, BSLParser.ReturnStatementContext.class);
  }

  /**
   * Универсальный метод для проверки контекста.
   *
   * @param expression выражение для проверки
   * @param contextClass класс контекста для проверки
   * @return true, если выражение находится в указанном контексте
   */
  protected static boolean insideContext(BSLParser.ExpressionContext expression,
                                        Class<? extends ParserRuleContext> contextClass) {
    if (expression == null) {
      return false;
    }
    var parent = expression.getParent();
    return parent != null && contextClass.isInstance(parent);
  }
}

