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
package com.github._1c_syntax.bsl.languageserver.semantictokens.strings;

/**
 * Контекст строки для определения типа обработки.
 */
public enum StringContext {
  /**
   * Строка в контексте вызова НСтр/NStr.
   */
  NSTR,
  /**
   * Строка в контексте вызова СтрШаблон/StrTemplate.
   */
  STR_TEMPLATE,
  /**
   * Строка в контексте вызова НСтр/NStr внутри СтрШаблон/StrTemplate или наоборот.
   * Например: СтрШаблон(НСтр("ru = 'Текст %1'"), Параметр)
   */
  NSTR_AND_STR_TEMPLATE;

  /**
   * Объединяет два контекста.
   * Если контексты разные (NSTR и STR_TEMPLATE), возвращает NSTR_AND_STR_TEMPLATE.
   *
   * @param other другой контекст для объединения
   * @return объединённый контекст
   */
  public StringContext combine(StringContext other) {
    if (this == other) {
      return this;
    }
    return NSTR_AND_STR_TEMPLATE;
  }
}

