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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Утилитный класс для работы со ссылками на общие модули.
 * <p>
 * Предоставляет методы для анализа конструкций получения ссылки на общий модуль
 * через ОбщегоНазначения.ОбщийМодуль("ИмяМодуля"), ОбщегоНазначенияКлиент.ОбщийМодуль("ИмяМодуля")
 * и других вариантов.
 */
@UtilityClass
public class ModuleReference {

  /**
   * Проверить, является ли expression вызовом получения ссылки на общий модуль.
   * Использует список паттернов из конфигурации.
   *
   * @param expression Контекст выражения
   * @param commonModuleAccessors Список паттернов "Модуль.Метод" или "Метод" для локального вызова
   * @return true, если это вызов метода получения общего модуля
   */
  public static boolean isCommonModuleExpression(
    BSLParser.ExpressionContext expression,
    @NonNull List<String> commonModuleAccessors
  ) {
    if (expression == null || commonModuleAccessors.isEmpty()) {
      return false;
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return false;
    }

    for (var member : members) {
      if (isCommonModuleExpressionMember(member, commonModuleAccessors)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Извлечь имя общего модуля из expression.
   *
   * @param expression Контекст выражения
   * @param commonModuleAccessors Список паттернов "Модуль.Метод" или "Метод" для локального вызова
   * @return Имя модуля, если удалось извлечь
   */
  public static Optional<String> extractCommonModuleName(
    BSLParser.ExpressionContext expression,
    @NonNull List<String> commonModuleAccessors
  ) {
    if (expression == null || commonModuleAccessors.isEmpty()) {
      return Optional.empty();
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return Optional.empty();
    }

    for (var member : members) {
      var result = extractCommonModuleNameFromMember(member, commonModuleAccessors);
      if (result.isPresent()) {
        return result;
      }
    }

    return Optional.empty();
  }

  // ===== Private helper methods =====

  private static boolean isCommonModuleExpressionMember(
    BSLParser.MemberContext member, 
    List<String> commonModuleAccessors
  ) {
    // Случай 1: IDENTIFIER - ОбщийМодуль("Name")
    if (member.IDENTIFIER() != null) {
      var identifier = member.IDENTIFIER().getText();
      if (isLocalMethodMatch(identifier, commonModuleAccessors)) {
        return true;
      }
    }
    
    // Случай 2: complexIdentifier с модификаторами
    var complexId = member.complexIdentifier();
    if (complexId == null) {
      return false;
    }
    
    var identifier = complexId.IDENTIFIER();
    if (identifier == null) {
      return false;
    }
    
    var idText = identifier.getText();
    
    // Случай 2a: Локальный вызов - ОбщийМодуль("Name")
    if (isLocalMethodMatch(idText, commonModuleAccessors)) {
      return true;
    }
    
    // Случай 2b: Модуль.Метод - ОбщегоНазначения.ОбщийМодуль("Name")
    return hasMatchingModifierMethodCall(complexId, idText, commonModuleAccessors);
  }

  private static boolean hasMatchingModifierMethodCall(
    BSLParser.ComplexIdentifierContext complexId, 
    String moduleName,
    List<String> commonModuleAccessors
  ) {
    return complexId.modifier().stream()
      .flatMap(modifier -> extractMethodNameFromModifier(modifier).stream())
      .anyMatch(methodName -> isModuleMethodMatch(methodName, moduleName, commonModuleAccessors));
  }

  private static Optional<String> extractMethodNameFromModifier(BSLParser.ModifierContext modifier) {
    return Optional.ofNullable(modifier.accessCall())
      .map(BSLParser.AccessCallContext::methodCall)
      .map(BSLParser.MethodCallContext::methodName)
      .map(BSLParser.MethodNameContext::IDENTIFIER)
      .map(TerminalNode::getText);
  }

  private static Optional<String> extractCommonModuleNameFromMember(
    BSLParser.MemberContext member, 
    List<String> commonModuleAccessors
  ) {
    var complexId = member.complexIdentifier();
    if (complexId == null) {
      return Optional.empty();
    }
    
    var identifier = complexId.IDENTIFIER();
    if (identifier != null) {
      var idText = identifier.getText();
      
      // Случай 1: Локальный вызов - ОбщийМодуль("Name")
      if (isLocalMethodMatch(idText, commonModuleAccessors)) {
        return extractModuleNameFromModifiers(complexId.modifier());
      }
      
      // Случай 2: Модуль.Метод - ОбщегоНазначения.ОбщийМодуль("Name")
      var result = extractModuleNameFromMatchingModifier(complexId, idText, commonModuleAccessors);
      if (result.isPresent()) {
        return result;
      }
    }
    
    // Случай 3: globalMethodCall внутри complexIdentifier
    return extractModuleNameFromGlobalMethodCall(complexId, commonModuleAccessors);
  }

  private static Optional<String> extractModuleNameFromMatchingModifier(
    BSLParser.ComplexIdentifierContext complexId, 
    String moduleName,
    List<String> commonModuleAccessors
  ) {
    return complexId.modifier().stream()
      .filter(modifier -> extractMethodNameFromModifier(modifier)
        .filter(methodName -> isModuleMethodMatch(methodName, moduleName, commonModuleAccessors))
        .isPresent())
      .findFirst()
      .flatMap(modifier -> Optional.ofNullable(modifier.accessCall())
        .map(BSLParser.AccessCallContext::methodCall)
        .map(BSLParser.MethodCallContext::doCall)
        .flatMap(ModuleReference::extractParameterFromDoCall));
  }

  private static Optional<String> extractModuleNameFromGlobalMethodCall(
    BSLParser.ComplexIdentifierContext complexId, 
    List<String> commonModuleAccessors
  ) {
    var globalMethodCall = complexId.globalMethodCall();
    if (globalMethodCall == null || globalMethodCall.methodName() == null) {
      return Optional.empty();
    }
    
    var methodName = globalMethodCall.methodName().IDENTIFIER();
    if (methodName != null && isLocalMethodMatch(methodName.getText(), commonModuleAccessors)) {
      return extractParameterFromDoCall(globalMethodCall.doCall());
    }
    return Optional.empty();
  }

  private static boolean isLocalMethodMatch(String methodName, List<String> patterns) {
    return isMethodMatch(methodName, "", patterns);
  }

  private static boolean isModuleMethodMatch(String methodName, String moduleName, List<String> patterns) {
    return isMethodMatch(methodName, moduleName, patterns);
  }

  /**
   * Проверяет, соответствует ли вызов метода одному из паттернов.
   *
   * @param methodName Имя вызываемого метода
   * @param moduleName Имя модуля (пустая строка для локального вызова)
   * @param patterns Список паттернов "Модуль.Метод" или "Метод"
   * @return true, если есть совпадение
   */
  private static boolean isMethodMatch(String methodName, String moduleName, List<String> patterns) {
    if (methodName == null) {
      return false;
    }
    
    var methodLower = methodName.toLowerCase(Locale.ENGLISH);
    var moduleLower = moduleName.toLowerCase(Locale.ENGLISH);
    var isLocalCall = moduleLower.isEmpty();
    
    for (var pattern : patterns) {
      var patternLower = pattern.toLowerCase(Locale.ENGLISH);
      
      if (patternLower.contains(".")) {
        // Паттерн "Модуль.Метод"
        if (!isLocalCall) {
          var parts = patternLower.split("\\.", 2);
          if (parts.length == 2 && parts[0].equals(moduleLower) && parts[1].equals(methodLower)) {
            return true;
          }
        }
      } else {
        // Паттерн "Метод" (локальный вызов)
        if (isLocalCall && patternLower.equals(methodLower)) {
          return true;
        }
      }
    }
    
    return false;
  }

  private static Optional<String> extractModuleNameFromModifiers(
    java.util.List<? extends BSLParser.ModifierContext> modifiers
  ) {
    for (var modifier : modifiers) {
      var moduleName = extractParameterFromDoCall(modifier.accessCall());
      if (moduleName.isPresent()) {
        return moduleName;
      }
    }
    return Optional.empty();
  }

  private static Optional<String> extractParameterFromDoCall(BSLParser.AccessCallContext accessCall) {
    return Optional.ofNullable(accessCall)
      .map(BSLParser.AccessCallContext::methodCall)
      .map(BSLParser.MethodCallContext::doCall)
      .flatMap(ModuleReference::extractParameterFromDoCall);
  }

  private static Optional<String> extractParameterFromDoCall(BSLParser.DoCallContext doCall) {
    return Optional.ofNullable(doCall)
      .map(BSLParser.DoCallContext::callParamList)
      .map(BSLParser.CallParamListContext::callParam)
      .filter(params -> !params.isEmpty())
      .map(params -> params.get(0))
      .map(BSLParser.CallParamContext::getText)
      .map(Strings::trimQuotes);
  }
}
