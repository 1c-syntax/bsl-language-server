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
package com.github._1c_syntax.bsl.languageserver.semantictokens.strings;

import com.github._1c_syntax.bsl.languageserver.utils.MultilingualStringAnalyser;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Visitor для поиска вызовов НСтр и СтрШаблон.
 * <p>
 * Собирает информацию о строковых токенах, находящихся в контексте
 * вызовов НСтр/NStr и СтрШаблон/StrTemplate.
 * <p>
 * Также поддерживает поиск строк-шаблонов, которые присвоены переменным,
 * а затем используются в вызове СтрШаблон.
 * <p>
 * Дополнительно поддерживает конфигурируемые функции-шаблонизаторы,
 * аналогичные СтрШаблон (например, СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку).
 */
public class SpecialContextVisitor extends BSLParserBaseVisitor<Void> {

  private static final Set<Integer> STRING_TYPES = Set.of(
    BSLLexer.STRING,
    BSLLexer.STRINGPART,
    BSLLexer.STRINGSTART,
    BSLLexer.STRINGTAIL
  );

  private final Map<Token, StringContext> contexts;
  private final ParsedStrTemplateMethods parsedMethods;

  /**
   * Предварительно разобранные паттерны функций-шаблонизаторов.
   * <p>
   * Структура:
   * - localMethods: Set методов для локального вызова (без модуля)
   * - moduleMethodPairs: Map из имени модуля -> Set методов этого модуля
   */
  public record ParsedStrTemplateMethods(
    Set<String> localMethods,
    Map<String, Set<String>> moduleMethodPairs
  ) {
    /**
     * Создаёт пустой объект без дополнительных методов.
     */
    public static ParsedStrTemplateMethods empty() {
      return new ParsedStrTemplateMethods(Set.of(), Map.of());
    }
  }

  /**
   * Разбирает список паттернов функций-шаблонизаторов один раз.
   * <p>
   * Вызывается один раз при инициализации и результат кэшируется.
   *
   * @param strTemplateMethods Список паттернов "Модуль.Метод" или "Метод" для локального вызова
   * @return Предварительно разобранные паттерны
   */
  public static ParsedStrTemplateMethods parseStrTemplateMethods(List<String> strTemplateMethods) {
    var localMethods = new HashSet<String>();
    var moduleMethodPairs = new HashMap<String, Set<String>>();

    if (strTemplateMethods == null) {
      return new ParsedStrTemplateMethods(localMethods, moduleMethodPairs);
    }

    for (var pattern : strTemplateMethods) {
      if (pattern == null || pattern.isBlank()) {
        continue;
      }
      var patternLower = pattern.toLowerCase(Locale.ENGLISH);

      if (patternLower.contains(".")) {
        var parts = patternLower.split("\\.", 2);
        if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
          moduleMethodPairs
            .computeIfAbsent(parts[0], k -> new HashSet<>())
            .add(parts[1]);
        }
      } else {
        localMethods.add(patternLower);
      }
    }

    return new ParsedStrTemplateMethods(localMethods, moduleMethodPairs);
  }

  /**
   * Создаёт visitor для сбора контекстов строк.
   *
   * @param contexts Map для заполнения контекстами строк
   */
  public SpecialContextVisitor(Map<Token, StringContext> contexts) {
    this(contexts, ParsedStrTemplateMethods.empty());
  }

  /**
   * Создаёт visitor для сбора контекстов строк с конфигурируемыми функциями-шаблонизаторами.
   *
   * @param contexts      Map для заполнения контекстами строк
   * @param parsedMethods Предварительно разобранные паттерны функций-шаблонизаторов
   */
  public SpecialContextVisitor(Map<Token, StringContext> contexts, ParsedStrTemplateMethods parsedMethods) {
    this.contexts = contexts;
    this.parsedMethods = parsedMethods;
  }

  @Override
  public Void visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    StringContext context = null;

    if (MultilingualStringAnalyser.isNStrCall(ctx)) {
      context = StringContext.NSTR;
    } else if (MultilingualStringAnalyser.isStrTemplateCall(ctx)) {
      context = StringContext.STR_TEMPLATE;
    } else if (isConfiguredStrTemplateCall(ctx)) {
      context = StringContext.STR_TEMPLATE;
    }

    if (context != null) {
      processMethodCallParams(ctx.doCall(), context, ctx);
    }

    return super.visitGlobalMethodCall(ctx);
  }

  @Override
  public Void visitCallStatement(BSLParser.CallStatementContext ctx) {
    // Обрабатываем вызовы вида Модуль.Метод(...) в отдельных statements (не в выражениях)
    if (ctx.IDENTIFIER() != null && ctx.accessCall() != null) {
      processModuleMethodCall(ctx.IDENTIFIER().getText(), ctx.accessCall(), ctx);
    }

    return super.visitCallStatement(ctx);
  }

  @Override
  public Void visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    // Обрабатываем вызовы вида Модуль.Метод(...) в выражениях (присвоениях и т.п.)
    var identifier = ctx.IDENTIFIER();
    if (identifier != null && ctx.modifier() != null && !ctx.modifier().isEmpty()) {
      for (var modifier : ctx.modifier()) {
        var accessCall = modifier.accessCall();
        if (accessCall != null) {
          processModuleMethodCall(identifier.getText(), accessCall, ctx);
        }
      }
    }

    return super.visitComplexIdentifier(ctx);
  }

  /**
   * Обрабатывает вызов метода модуля вида Модуль.Метод(...).
   */
  private void processModuleMethodCall(
    String moduleName,
    BSLParser.AccessCallContext accessCall,
    org.antlr.v4.runtime.ParserRuleContext ctx
  ) {
    var methodCall = accessCall.methodCall();
    if (methodCall == null || methodCall.methodName() == null) {
      return;
    }

    var methodName = methodCall.methodName().getText().toLowerCase(Locale.ENGLISH);
    var moduleNameLower = moduleName.toLowerCase(Locale.ENGLISH);

    if (isModuleMethodMatch(moduleNameLower, methodName)) {
      var doCall = methodCall.doCall();
      if (doCall != null) {
        processMethodCallParams(doCall, StringContext.STR_TEMPLATE, ctx);
      }
    }
  }

  /**
   * Проверяет, является ли вызов глобального метода конфигурируемым шаблонизатором.
   */
  private boolean isConfiguredStrTemplateCall(BSLParser.GlobalMethodCallContext ctx) {
    var methodName = ctx.methodName().getText().toLowerCase(Locale.ENGLISH);
    return parsedMethods.localMethods().contains(methodName);
  }

  /**
   * Проверяет, соответствует ли пара "модуль.метод" конфигурируемым паттернам.
   */
  private boolean isModuleMethodMatch(String moduleName, String methodName) {
    var moduleMethods = parsedMethods.moduleMethodPairs().get(moduleName);
    return moduleMethods != null && moduleMethods.contains(methodName);
  }

  /**
   * Обрабатывает параметры вызова метода.
   */
  private void processMethodCallParams(
    BSLParser.DoCallContext doCall,
    StringContext context,
    ParserRuleContext callContext
  ) {
    var callParams = doCall.callParamList().callParam();
    if (!callParams.isEmpty()) {
      var firstParam = callParams.get(0);
      var stringTokens = getStringTokensFromParam(firstParam);

      if (stringTokens.isEmpty() && context == StringContext.STR_TEMPLATE) {
        // Первый параметр не строковый литерал - возможно, это переменная
        // Пытаемся найти присвоение этой переменной
        stringTokens = findStringTokensFromVariable(firstParam, callContext);
      }

      for (Token token : stringTokens) {
        contexts.merge(token, context, StringContext::combine);
      }
    }
  }

  private List<Token> getStringTokensFromParam(BSLParser.CallParamContext callParam) {
    List<Token> stringTokens = new ArrayList<>();
    var tokens = Trees.getTokens(callParam);

    for (Token token : tokens) {
      if (STRING_TYPES.contains(token.getType())) {
        stringTokens.add(token);
      }
    }

    return stringTokens;
  }

  /**
   * Ищет строковые токены из присвоения переменной, используемой в СтрШаблон.
   * <p>
   * Например, для кода:
   * <pre>
   * НовыйШаблон = "%1 %2";
   * Результат = СтрШаблон(НовыйШаблон, А, Б);
   * </pre>
   * найдёт строку "%1 %2" и вернёт её токены.
   * <p>
   * Также поддерживает случай:
   * <pre>
   * Шаблон = НСтр("ru = 'Сценарий %1'");
   * Результат = СтрШаблон(Шаблон, Параметр);
   * </pre>
   * найдёт строку "ru = 'Сценарий %1'" и вернёт её токены.
   */
  private List<Token> findStringTokensFromVariable(
    BSLParser.CallParamContext callParam,
    ParserRuleContext callContext
  ) {
    // Получаем имя переменной из первого параметра
    var varName = extractVariableName(callParam);
    if (varName == null) {
      return List.of();
    }

    // Ищем присвоение этой переменной выше по коду
    var assignmentExpression = findAssignedExpression(varName, callContext);
    if (assignmentExpression == null) {
      return List.of();
    }

    // Извлекаем строковые токены из присвоения
    return extractStringTokensFromExpression(assignmentExpression);
  }

  @Nullable
  private String extractVariableName(BSLParser.CallParamContext callParam) {
    return Optional.of(callParam)
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .filter(members -> members.size() == 1)
      .map(members -> members.get(0))
      .map(BSLParser.MemberContext::complexIdentifier)
      .map(BSLParser.ComplexIdentifierContext::IDENTIFIER)
      .map(id -> id.getSymbol().getText())
      .orElse(null);
  }

  private BSLParser.@Nullable ExpressionContext findAssignedExpression(String varName, ParserRuleContext callContext) {
    // Находим statement, содержащий вызов СтрШаблон
    var currentStatement = Trees.getRootParent(callContext, BSLParser.RULE_statement);
    if (currentStatement == null) {
      return null;
    }

    // Ищем предыдущие statements
    var prevStatement = getPreviousStatement((BSLParser.StatementContext) currentStatement);
    while (prevStatement != null) {
      var assignment = prevStatement.assignment();
      if (assignment != null && isAssignmentForVar(varName, assignment)) {
        // Нашли присвоение - возвращаем выражение
        return assignment.expression();
      }
      prevStatement = getPreviousStatement(prevStatement);
    }

    return null;
  }

  private BSLParser.@Nullable StatementContext getPreviousStatement(BSLParser.StatementContext statement) {
    var parent = statement.getParent();
    if (parent == null) {
      return null;
    }

    var children = parent.children;
    if (children == null) {
      return null;
    }

    int pos = children.indexOf(statement);
    for (int i = pos - 1; i >= 0; i--) {
      var child = children.get(i);
      if (child instanceof BSLParser.StatementContext prevStmt) {
        return prevStmt;
      }
    }

    return null;
  }

  private boolean isAssignmentForVar(String varName, BSLParser.AssignmentContext assignment) {
    var lValue = assignment.lValue();
    if (lValue == null) {
      return false;
    }
    var identifier = lValue.IDENTIFIER();
    return identifier != null && identifier.getText().equalsIgnoreCase(varName);
  }

  /**
   * Извлекает строковые токены из выражения.
   * Поддерживает как простые строковые литералы, так и вызовы НСтр.
   */
  private List<Token> extractStringTokensFromExpression(BSLParser.ExpressionContext expression) {
    // Пробуем получить простую строку
    var stringContext = Optional.of(expression)
      .map(BSLParser.ExpressionContext::member)
      .filter(members -> members.size() == 1)
      .map(members -> members.get(0))
      .map(BSLParser.MemberContext::constValue)
      .map(BSLParser.ConstValueContext::string)
      .orElse(null);

    if (stringContext != null) {
      return getStringTokensFromContext(stringContext);
    }

    // Пробуем получить вызов НСтр
    var globalMethodCall = Optional.of(expression)
      .map(BSLParser.ExpressionContext::member)
      .filter(members -> members.size() == 1)
      .map(members -> members.get(0))
      .map(BSLParser.MemberContext::complexIdentifier)
      .map(BSLParser.ComplexIdentifierContext::globalMethodCall)
      .orElse(null);

    if (globalMethodCall != null && MultilingualStringAnalyser.isNStrCall(globalMethodCall)) {
      var callParams = globalMethodCall.doCall().callParamList().callParam();
      if (!callParams.isEmpty()) {
        return getStringTokensFromParam(callParams.get(0));
      }
    }

    return List.of();
  }

  private List<Token> getStringTokensFromContext(BSLParser.StringContext stringContext) {
    List<Token> stringTokens = new ArrayList<>();
    var tokens = Trees.getTokens(stringContext);

    for (Token token : tokens) {
      if (STRING_TYPES.contains(token.getType())) {
        stringTokens.add(token);
      }
    }

    return stringTokens;
  }
}
