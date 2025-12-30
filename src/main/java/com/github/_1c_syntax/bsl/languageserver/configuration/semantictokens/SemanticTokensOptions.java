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
package com.github._1c_syntax.bsl.languageserver.configuration.semantictokens;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройки для семантических токенов.
 * <p>
 * Позволяет указать дополнительные функции-шаблонизаторы строк,
 * аналогичные СтрШаблон/StrTemplate, для подсветки плейсхолдеров (%1, %2 и т.д.).
 */
@Data
@AllArgsConstructor(onConstructor = @__({@JsonCreator(mode = JsonCreator.Mode.DISABLED)}))
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticTokensOptions {

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
  private List<String> strTemplateMethods = new ArrayList<>(List.of(
    // Локальный вызов
    "ПодставитьПараметрыВСтроку",
    "SubstituteParametersToString",
    // Стандартный модуль БСП
    "СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку",
    // Английский вариант
    "StringFunctionsClientServer.SubstituteParametersToString"
  ));
}
