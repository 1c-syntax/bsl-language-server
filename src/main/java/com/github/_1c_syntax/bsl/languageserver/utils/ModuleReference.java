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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
   * Предварительно разобранные паттерны доступа к общим модулям.
   * <p>
   * Структура:
   * - localMethods: Set методов для локального вызова (без модуля)
   * - moduleMethodPairs: Map из имени модуля -> Set методов этого модуля
   */
  public record ParsedAccessors(
    Set<String> localMethods,
    Map<String, Set<String>> moduleMethodPairs
  ) {}

  /**
   * Разбирает список паттернов доступа к общим модулям один раз.
   * <p>
   * Вызывается один раз при инициализации и результат кэшируется.
   *
   * @param commonModuleAccessors Список паттернов "Модуль.Метод" или "Метод" для локального вызова
   * @return Предварительно разобранные паттерны
   */
  public static ParsedAccessors parseAccessors(@NonNull List<String> commonModuleAccessors) {
    var localMethods = new HashSet<String>();
    var moduleMethodPairs = new HashMap<String, Set<String>>();

    for (var pattern : commonModuleAccessors) {
      var patternLower = pattern.toLowerCase(Locale.ENGLISH);

      if (patternLower.contains(".")) {
        var parts = patternLower.split("\\.", 2);
        if (parts.length == 2) {
          moduleMethodPairs
            .computeIfAbsent(parts[0], k -> new HashSet<>())
            .add(parts[1]);
        }
      } else {
        localMethods.add(patternLower);
      }
    }

    return new ParsedAccessors(localMethods, moduleMethodPairs);
  }

  /**
   * Проверить, является ли expression вызовом получения ссылки на общий модуль.
   * Использует предварительно разобранные паттерны.
   *
   * @param expression Контекст выражения
   * @param parsedAccessors Предварительно разобранные паттерны
   * @return true, если это вызов метода получения общего модуля
   */
  public static boolean isCommonModuleExpression(
    BSLParser.ExpressionContext expression,
    @NonNull ParsedAccessors parsedAccessors
  ) {
    if (expression == null) {
      return false;
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return false;
    }

    for (var member : members) {
      if (isCommonModuleExpressionMember(member, parsedAccessors)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Извлечь имя общего модуля из expression.
   *
   * @param expression Контекст выражения
   * @param parsedAccessors Предварительно разобранные паттерны
   * @return Имя модуля, если удалось извлечь
   */
  public static Optional<String> extractCommonModuleName(
    BSLParser.ExpressionContext expression,
    @NonNull ParsedAccessors parsedAccessors
  ) {
    if (expression == null) {
      return Optional.empty();
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return Optional.empty();
    }

    for (var member : members) {
      var result = extractCommonModuleNameFromMember(member, parsedAccessors);
      if (result.isPresent()) {
        return result;
      }
    }

    return Optional.empty();
  }

  // ===== Private helper methods =====

  private static boolean isCommonModuleExpressionMember(
    BSLParser.MemberContext member,
    ParsedAccessors parsedAccessors
  ) {
    // Случай 1: IDENTIFIER - ОбщийМодуль("Name")
    if (member.IDENTIFIER() != null) {
      var identifier = member.IDENTIFIER().getText();
      if (isLocalMethodMatch(identifier, parsedAccessors)) {
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
    if (isLocalMethodMatch(idText, parsedAccessors)) {
      return true;
    }

    // Случай 2b: Модуль.Метод - ОбщегоНазначения.ОбщийМодуль("Name")
    return hasMatchingModifierMethodCall(complexId, idText, parsedAccessors);
  }

  private static boolean hasMatchingModifierMethodCall(
    BSLParser.ComplexIdentifierContext complexId,
    String moduleName,
    ParsedAccessors parsedAccessors
  ) {
    return complexId.modifier().stream()
      .flatMap(modifier -> extractMethodNameFromModifier(modifier).stream())
      .anyMatch(methodName -> isModuleMethodMatch(methodName, moduleName, parsedAccessors));
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
    ParsedAccessors parsedAccessors
  ) {
    var complexId = member.complexIdentifier();
    if (complexId == null) {
      return Optional.empty();
    }

    var identifier = complexId.IDENTIFIER();
    if (identifier != null) {
      var idText = identifier.getText();

      // Случай 1: Локальный вызов - ОбщийМодуль("Name")
      if (isLocalMethodMatch(idText, parsedAccessors)) {
        return extractModuleNameFromModifiers(complexId.modifier());
      }

      // Случай 2: Модуль.Метод - ОбщегоНазначения.ОбщийМодуль("Name")
      var result = extractModuleNameFromMatchingModifier(complexId, idText, parsedAccessors);
      if (result.isPresent()) {
        return result;
      }
    }

    // Случай 3: globalMethodCall внутри complexIdentifier
    return extractModuleNameFromGlobalMethodCall(complexId, parsedAccessors);
  }

  private static Optional<String> extractModuleNameFromMatchingModifier(
    BSLParser.ComplexIdentifierContext complexId,
    String moduleName,
    ParsedAccessors parsedAccessors
  ) {
    return complexId.modifier().stream()
      .filter(modifier -> extractMethodNameFromModifier(modifier)
        .filter(methodName -> isModuleMethodMatch(methodName, moduleName, parsedAccessors))
        .isPresent())
      .findFirst()
      .flatMap(modifier -> Optional.ofNullable(modifier.accessCall())
        .map(BSLParser.AccessCallContext::methodCall)
        .map(BSLParser.MethodCallContext::doCall)
        .flatMap(ModuleReference::extractParameterFromDoCall));
  }

  private static Optional<String> extractModuleNameFromGlobalMethodCall(
    BSLParser.ComplexIdentifierContext complexId,
    ParsedAccessors parsedAccessors
  ) {
    var globalMethodCall = complexId.globalMethodCall();
    if (globalMethodCall == null || globalMethodCall.methodName() == null) {
      return Optional.empty();
    }

    var methodName = globalMethodCall.methodName().IDENTIFIER();
    if (methodName != null && isLocalMethodMatch(methodName.getText(), parsedAccessors)) {
      return extractParameterFromDoCall(globalMethodCall.doCall());
    }
    return Optional.empty();
  }

  private static boolean isLocalMethodMatch(String methodName, ParsedAccessors parsedAccessors) {
    if (methodName == null) {
      return false;
    }
    return parsedAccessors.localMethods().contains(methodName.toLowerCase(Locale.ENGLISH));
  }

  private static boolean isModuleMethodMatch(String methodName, String moduleName, ParsedAccessors parsedAccessors) {
    if (methodName == null || moduleName == null) {
      return false;
    }
    var moduleMethods = parsedAccessors.moduleMethodPairs().get(moduleName.toLowerCase(Locale.ENGLISH));
    return moduleMethods != null && moduleMethods.contains(methodName.toLowerCase(Locale.ENGLISH));
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
