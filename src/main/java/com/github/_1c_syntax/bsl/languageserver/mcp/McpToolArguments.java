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
package com.github._1c_syntax.bsl.languageserver.mcp;

import lombok.experimental.UtilityClass;

import java.util.Map;

/**
 * Утилиты для типизированного чтения аргументов вызова инструмента.
 */
@UtilityClass
public class McpToolArguments {

  /**
   * Прочитать обязательный строковый параметр.
   *
   * @param arguments Аргументы вызова.
   * @param name Имя параметра.
   * @return Непустое значение параметра.
   */
  public String requireString(Map<String, Object> arguments, String name) {
    var value = arguments.get(name);
    if (!(value instanceof String string) || string.isBlank()) {
      throw new IllegalArgumentException("Parameter '" + name + "' is required and must be a non-empty string");
    }
    return string;
  }

  /**
   * Прочитать обязательный целочисленный параметр.
   *
   * @param arguments Аргументы вызова.
   * @param name Имя параметра.
   * @return Значение параметра.
   */
  public int requireInt(Map<String, Object> arguments, String name) {
    var value = arguments.get(name);
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException("Parameter '" + name + "' is required and must be an integer");
    }
    return number.intValue();
  }
}
