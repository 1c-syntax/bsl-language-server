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
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Утилитный класс для работы с вызовами ОбщийМодуль и ссылками на модули менеджеров.
 * <p>
 * Предоставляет методы для анализа конструкций получения ссылки на общий модуль
 * через ОбщегоНазначения.ОбщийМодуль("ИмяМодуля"), ОбщегоНазначенияКлиент.ОбщийМодуль("ИмяМодуля")
 * и других вариантов, а также для работы с модулями менеджеров (Справочники.Имя, Документы.Имя и т.д.)
 */
@UtilityClass
public class CommonModuleReference {

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
    List<String> commonModuleAccessors
  ) {
    if (expression == null || commonModuleAccessors == null || commonModuleAccessors.isEmpty()) {
      return false;
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return false;
    }

    for (var member : members) {
      // Случай 1: IDENTIFIER - ОбщийМодуль("Name")
      if (member.IDENTIFIER() != null) {
        var identifier = member.IDENTIFIER().getText();
        if (isMethodMatch(identifier, null, commonModuleAccessors)) {
          return true;
        }
      }
      
      // Случай 2: complexIdentifier с модификаторами
      var complexId = member.complexIdentifier();
      if (complexId != null) {
        var identifier = complexId.IDENTIFIER();
        if (identifier != null) {
          var idText = identifier.getText();
          
          // Случай 2a: Локальный вызов - ОбщийМодуль("Name")
          if (isMethodMatch(idText, null, commonModuleAccessors)) {
            return true;
          }
          
          // Случай 2b: Модуль.Метод - ОбщегоНазначения.ОбщийМодуль("Name")
          for (var modifier : complexId.modifier()) {
            var accessCall = modifier.accessCall();
            if (accessCall != null && accessCall.methodCall() != null) {
              var methodCall = accessCall.methodCall();
              var methodName = methodCall.methodName();
              if (methodName != null && methodName.IDENTIFIER() != null) {
                var methodText = methodName.IDENTIFIER().getText();
                if (isMethodMatch(methodText, idText, commonModuleAccessors)) {
                  return true;
                }
              }
            }
          }
        }
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
    List<String> commonModuleAccessors
  ) {
    if (expression == null || commonModuleAccessors == null || commonModuleAccessors.isEmpty()) {
      return Optional.empty();
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return Optional.empty();
    }

    for (var member : members) {
      var complexId = member.complexIdentifier();
      if (complexId != null) {
        var identifier = complexId.IDENTIFIER();
        if (identifier != null) {
          var idText = identifier.getText();
          
          // Случай 1: Локальный вызов - ОбщийМодуль("Name")
          if (isMethodMatch(idText, null, commonModuleAccessors)) {
            return extractModuleNameFromModifiers(complexId.modifier());
          }
          
          // Случай 2: Модуль.Метод - ОбщегоНазначения.ОбщийМодуль("Name")
          for (var modifier : complexId.modifier()) {
            var accessCall = modifier.accessCall();
            if (accessCall != null && accessCall.methodCall() != null) {
              var methodCall = accessCall.methodCall();
              var methodName = methodCall.methodName();
              if (methodName != null && methodName.IDENTIFIER() != null) {
                var methodText = methodName.IDENTIFIER().getText();
                if (isMethodMatch(methodText, idText, commonModuleAccessors)) {
                  return extractParameterFromDoCall(methodCall.doCall());
                }
              }
            }
          }
        }
        
        // Случай 3: globalMethodCall внутри complexIdentifier
        var globalMethodCall = complexId.globalMethodCall();
        if (globalMethodCall != null && globalMethodCall.methodName() != null) {
          var methodName = globalMethodCall.methodName().IDENTIFIER();
          if (methodName != null && isMethodMatch(methodName.getText(), null, commonModuleAccessors)) {
            return extractParameterFromDoCall(globalMethodCall.doCall());
          }
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Проверяет, соответствует ли вызов метода одному из паттернов.
   *
   * @param methodName Имя вызываемого метода
   * @param moduleName Имя модуля (null для локального вызова)
   * @param patterns Список паттернов "Модуль.Метод" или "Метод"
   * @return true, если есть совпадение
   */
  private static boolean isMethodMatch(String methodName, String moduleName, List<String> patterns) {
    if (methodName == null) {
      return false;
    }
    
    var methodLower = methodName.toLowerCase(Locale.ENGLISH);
    var moduleLower = moduleName != null ? moduleName.toLowerCase(Locale.ENGLISH) : null;
    
    for (var pattern : patterns) {
      var patternLower = pattern.toLowerCase(Locale.ENGLISH);
      
      if (patternLower.contains(".")) {
        // Паттерн "Модуль.Метод"
        var parts = patternLower.split("\\.", 2);
        if (parts.length == 2 && moduleLower != null) {
          if (parts[0].equals(moduleLower) && parts[1].equals(methodLower)) {
            return true;
          }
        }
      } else {
        // Паттерн "Метод" (локальный вызов)
        if (moduleName == null && patternLower.equals(methodLower)) {
          return true;
        }
      }
    }
    
    return false;
  }

  private static Optional<String> extractModuleNameFromModifiers(
    java.util.List<? extends BSLParser.ModifierContext> modifiers) {
    
    for (var modifier : modifiers) {
      var moduleName = extractParameterFromDoCall(modifier.accessCall());
      if (moduleName.isPresent()) {
        return moduleName;
      }
    }
    return Optional.empty();
  }

  private static Optional<String> extractParameterFromDoCall(BSLParser.AccessCallContext accessCall) {
    if (accessCall == null) {
      return Optional.empty();
    }

    var methodCall = accessCall.methodCall();
    if (methodCall == null) {
      return Optional.empty();
    }

    return extractParameterFromDoCall(methodCall.doCall());
  }

  private static Optional<String> extractParameterFromDoCall(BSLParser.DoCallContext doCall) {
    if (doCall == null) {
      return Optional.empty();
    }

    var callParamList = doCall.callParamList();
    if (callParamList == null) {
      return Optional.empty();
    }

    var params = callParamList.callParam();
    if (params.isEmpty()) {
      return Optional.empty();
    }

    // Берем первый параметр - имя модуля
    var firstParam = params.get(0);
    return Optional.ofNullable(firstParam.getText())
      .map(Strings::trimQuotes);
  }

  /**
   * Проверить, является ли expression обращением к модулю менеджера.
   * Распознает паттерны типа: Справочники.ИмяСправочника, Документы.ИмяДокумента и т.д.
   * Использует MDOType.fromValue() для определения допустимых типов.
   *
   * @param expression Контекст выражения
   * @return true, если это обращение к модулю менеджера
   */
  public static boolean isManagerModuleExpression(BSLParser.ExpressionContext expression) {
    if (expression == null) {
      return false;
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return false;
    }

    for (var member : members) {
      var complexId = member.complexIdentifier();
      if (complexId != null) {
        var identifier = complexId.IDENTIFIER();
        if (identifier != null) {
          var idText = identifier.getText();
          // Используем MDOType.fromValue() для определения типа менеджера
          var mdoType = MDOType.fromValue(idText);
          if (mdoType.isPresent() && 
              ModuleType.byMDOType(mdoType.get()).contains(ModuleType.ManagerModule)) {
            // Должен быть хотя бы один модификатор (имя объекта)
            return !complexId.modifier().isEmpty();
          }
        }
      }
    }

    return false;
  }

  /**
   * Извлечь информацию о модуле менеджера из expression.
   * Использует MDOType.fromValue() для определения типа менеджера.
   * 
   * @param expression Контекст выражения
   * @return Пара (тип менеджера, имя объекта), например ("Справочники", "Номенклатура")
   */
  public static Optional<ManagerModuleInfo> extractManagerModuleInfo(BSLParser.ExpressionContext expression) {
    if (expression == null) {
      return Optional.empty();
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return Optional.empty();
    }

    for (var member : members) {
      var complexId = member.complexIdentifier();
      if (complexId != null) {
        var identifier = complexId.IDENTIFIER();
        if (identifier != null) {
          var managerType = identifier.getText();
          // Используем MDOType.fromValue() для проверки типа менеджера
          var mdoType = MDOType.fromValue(managerType);
          if (mdoType.isPresent() && 
              ModuleType.byMDOType(mdoType.get()).contains(ModuleType.ManagerModule)) {
            // Ищем имя объекта в первом модификаторе
            var modifiers = complexId.modifier();
            if (!modifiers.isEmpty()) {
              var firstModifier = modifiers.get(0);
              if (firstModifier.accessProperty() != null && 
                  firstModifier.accessProperty().IDENTIFIER() != null) {
                var objectName = firstModifier.accessProperty().IDENTIFIER().getText();
                return Optional.of(new ManagerModuleInfo(managerType, objectName));
              }
            }
          }
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Информация о модуле менеджера.
   *
   * @param managerType Тип менеджера (Справочники, Документы и т.д.)
   * @param objectName Имя объекта метаданных
   */
  public record ManagerModuleInfo(String managerType, String objectName) {
  }
}
