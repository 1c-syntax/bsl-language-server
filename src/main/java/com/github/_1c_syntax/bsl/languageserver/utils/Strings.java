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

import lombok.experimental.UtilityClass;

/**
 * Утилитный класс для работы со строками.
 * <p>
 * Предоставляет методы для обработки строк, используемых в BSL.
 */
@UtilityClass
public class Strings {

  private static final int MIN_TEXT_SIZE = 2;

  /**
   * Удалить кавычки из строки и обрезать пробелы.
   *
   * @param text Текст для обработки
   * @return Текст без кавычек и пробелов
   */
  public static String trimQuotes(String text) {
    return trimLastQuote(trimFirstQuote(text)).strip();
  }

  private static String trimFirstQuote(String text) {
    if (text.length() > MIN_TEXT_SIZE && text.charAt(0) == '\"') {
      return text.substring(1);
    }
    return text;
  }

  private static String trimLastQuote(String text) {
    if (text.length() > MIN_TEXT_SIZE && text.charAt(text.length() - 1) == '\"') {
      return text.substring(0, text.length() - 1);
    }
    return text;
  }

}
