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
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.experimental.UtilityClass;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Утилитный класс для работы с вызовами ОбщийМодуль.
 * <p>
 * Предоставляет методы для анализа конструкций получения ссылки на общий модуль
 * через ОбщегоНазначения.ОбщийМодуль("ИмяМодуля") или ОбщийМодуль("ИмяМодуля")
 */
@UtilityClass
public class CommonModuleReference {

  private static final Pattern COMMON_MODULE_METHOD = CaseInsensitivePattern.compile(
    "^(ОбщийМодуль|CommonModule)$");
  
  private static final Pattern COMMON_USE_MODULE = CaseInsensitivePattern.compile(
    "^(ОбщегоНазначения|CommonUse)$");

  /**
   * Проверить, является ли expression вызовом получения ссылки на общий модуль.
   * Распознает паттерны:
   * - ОбщегоНазначения.ОбщийМодуль("ИмяМодуля")
   * - ОбщийМодуль("ИмяМодуля")
   *
   * @param expression Контекст выражения
   * @return true, если это вызов ОбщийМодуль
   */
  public static boolean isCommonModuleExpression(BSLParser.ExpressionContext expression) {
    if (expression == null) {
      return false;
    }

    var members = expression.member();
    if (members.isEmpty()) {
      return false;
    }

    // В выражении могут быть один или несколько members
    // Для простого вызова ОбщийМодуль("Name") - один member с complexIdentifier
    // Для ОбщегоНазначения.ОбщийМодуль("Name") - один member с complexIdentifier и modifier
    
    for (var member : members) {
      var complexId = member.complexIdentifier();
      if (complexId != null) {
        // Проверяем базовый идентификатор
        var identifier = complexId.IDENTIFIER();
        if (identifier != null) {
          var idText = identifier.getText();
          
          // Случай 1: ОбщийМодуль("Name")
          if (COMMON_MODULE_METHOD.matcher(idText).matches()) {
            return true;
          }
          
          // Случай 2: ОбщегоНазначения.ОбщийМодуль("Name")
          if (COMMON_USE_MODULE.matcher(idText).matches()) {
            // Проверяем, есть ли вызов ОбщийМодуль в модификаторах
            for (var modifier : complexId.modifier()) {
              if (isCommonModuleCallInModifier(modifier)) {
                return true;
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
   * @return Имя модуля, если удалось извлечь
   */
  public static Optional<String> extractCommonModuleName(BSLParser.ExpressionContext expression) {
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
          var idText = identifier.getText();
          
          // Случай 1: ОбщийМодуль("Name") - параметры в модификаторах
          if (COMMON_MODULE_METHOD.matcher(idText).matches()) {
            return extractModuleNameFromModifiers(complexId.modifier());
          }
          
          // Случай 2: ОбщегоНазначения.ОбщийМодуль("Name")
          if (COMMON_USE_MODULE.matcher(idText).matches()) {
            for (var modifier : complexId.modifier()) {
              var moduleName = extractModuleNameFromModifier(modifier);
              if (moduleName.isPresent()) {
                return moduleName;
              }
            }
          }
        }
      }
    }

    return Optional.empty();
  }

  private static boolean isCommonModuleCallInModifier(BSLParser.ModifierContext modifier) {
    var accessCall = modifier.accessCall();
    if (accessCall == null) {
      return false;
    }

    var methodCall = accessCall.methodCall();
    if (methodCall == null) {
      return false;
    }

    var methodName = methodCall.methodName();
    if (methodName == null || methodName.IDENTIFIER() == null) {
      return false;
    }

    return COMMON_MODULE_METHOD.matcher(methodName.IDENTIFIER().getText()).matches();
  }

  private static Optional<String> extractModuleNameFromModifier(BSLParser.ModifierContext modifier) {
    var accessCall = modifier.accessCall();
    if (accessCall == null) {
      return Optional.empty();
    }

    var methodCall = accessCall.methodCall();
    if (methodCall == null || methodCall.methodName() == null) {
      return Optional.empty();
    }

    var methodName = methodCall.methodName().IDENTIFIER();
    if (methodName == null || !COMMON_MODULE_METHOD.matcher(methodName.getText()).matches()) {
      return Optional.empty();
    }

    return extractParameterFromDoCall(methodCall.doCall());
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
}
