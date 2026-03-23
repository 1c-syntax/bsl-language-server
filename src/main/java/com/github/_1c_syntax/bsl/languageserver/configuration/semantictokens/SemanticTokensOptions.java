/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.configuration.semantictokens;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Настройки для семантических токенов.
 * <p>
 * Позволяет указать дополнительные функции-шаблонизаторы строк,
 * аналогичные СтрШаблон/StrTemplate, для подсветки плейсхолдеров (%1, %2 и т.д.),
 * а также функции для лямбда-выражений (например, из библиотеки sfaqer/lambdas).
 */
@Getter
@AllArgsConstructor(onConstructor_ = {@JsonCreator(mode = JsonCreator.Mode.DISABLED)})
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticTokensOptions {

  private static final List<String> DEFAULT_STR_TEMPLATE_METHODS = List.of(
    // Локальный вызов
    "ПодставитьПараметрыВСтроку",
    "SubstituteParametersToString",
    // Стандартный модуль БСП
    "СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку",
    // Английский вариант
    "StringFunctionsClientServer.SubstituteParametersToString"
  );

  private static final List<String> DEFAULT_LAMBDA_METHODS = List.of(
    // sfaqer/lambdas - основной API
    "Лямбда.Выражение"
  );

  /**
   * Список паттернов "Модуль.Метод" для функций-шаблонизаторов строк.
   * <p>
   * Строки внутри вызовов этих функций будут подсвечиваться так же,
   * как строки в СтрШаблон/StrTemplate (с выделением плейсхолдеров %1, %2 и т.д.).
   * <p>
   * Формат: "ИмяМодуля.ИмяМетода", например:
   * <ul>
   *   <li>"СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку"</li>
   *   <li>"StringFunctionsClientServer.SubstituteParametersToString"</li>
   *   <li>"ПодставитьПараметрыВСтроку" - для локального вызова без указания модуля</li>
   * </ul>
   * <p>
   * По умолчанию включает стандартные варианты из БСП.
   */
  private List<String> strTemplateMethods = new ArrayList<>(DEFAULT_STR_TEMPLATE_METHODS);

  /**
   * Список паттернов "Модуль.Метод" для функций, принимающих лямбда-выражения в виде строк.
   * <p>
   * Содержимое строк внутри вызовов этих функций будет интерпретироваться как BSL-выражение
   * и подсвечиваться с выделением ключевых слов, операторов и чисел.
   * <p>
   * Формат: "ИмяМодуля.ИмяМетода", например:
   * <ul>
   *   <li>"Лямбда.Выражение" - вызов из библиотеки sfaqer/lambdas</li>
   * </ul>
   * <p>
   * По умолчанию включает стандартные варианты из sfaqer/lambdas.
   */
  private List<String> lambdaMethods = new ArrayList<>(DEFAULT_LAMBDA_METHODS);

  /**
   * Кэшированные разобранные паттерны функций-шаблонизаторов.
   */
  @JsonIgnore
  private ParsedStrTemplateMethods parsedStrTemplateMethods = parseMethodPatterns(DEFAULT_STR_TEMPLATE_METHODS);

  /**
   * Кэшированные разобранные паттерны лямбда-функций.
   */
  @JsonIgnore
  private ParsedStrTemplateMethods parsedLambdaMethods = parseMethodPatterns(DEFAULT_LAMBDA_METHODS);

  /**
   * Устанавливает список паттернов функций-шаблонизаторов и пересчитывает кэш.
   *
   * @param strTemplateMethods Список паттернов
   */
  public void setStrTemplateMethods(List<String> strTemplateMethods) {
    this.strTemplateMethods = strTemplateMethods;
    this.parsedStrTemplateMethods = parseMethodPatterns(strTemplateMethods);
  }

  /**
   * Устанавливает список паттернов лямбда-функций и пересчитывает кэш.
   *
   * @param lambdaMethods Список паттернов
   */
  public void setLambdaMethods(List<String> lambdaMethods) {
    this.lambdaMethods = lambdaMethods;
    this.parsedLambdaMethods = parseMethodPatterns(lambdaMethods);
  }

  /**
   * Возвращает предварительно разобранные паттерны функций-шаблонизаторов.
   *
   * @return Разобранные паттерны для быстрого поиска
   */
  @JsonIgnore
  public ParsedStrTemplateMethods getParsedStrTemplateMethods() {
    return parsedStrTemplateMethods;
  }

  /**
   * Возвращает предварительно разобранные паттерны лямбда-функций.
   *
   * @return Разобранные паттерны для быстрого поиска
   */
  @JsonIgnore
  public ParsedStrTemplateMethods getParsedLambdaMethods() {
    return parsedLambdaMethods;
  }

  private static ParsedStrTemplateMethods parseMethodPatterns(List<String> methods) {
    var localMethods = new HashSet<String>();
    var moduleMethodPairs = new HashMap<String, Set<String>>();

    for (var pattern : methods) {
      if (pattern.isBlank()) {
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
}
