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
package com.github._1c_syntax.bsl.languageserver.recognizer;

/**
 * Детектор окончания строки заданными символами.
 * <p>
 * Проверяет, заканчивается ли строка одним из указанных символов
 * (например, точкой с запятой).
 */
public class EndWithDetector extends AbstractDetector {

  private final char[] endOfLines;

  /**
   * Создать детектор окончаний строк.
   *
   * @param probability Вероятность обнаружения
   * @param endOfLines Массив символов-окончаний
   */
  public EndWithDetector(double probability, char... endOfLines) {
    super(probability);
    this.endOfLines = endOfLines.clone();
  }

  @Override
  int scan(String line) {
    for (int index = line.length() - 1; index >= 0; index--) {
      char character = line.charAt(index);

      for (char endOfLine : endOfLines) {
        if (character == endOfLine) {
          return 1;
        }
      }

      if (!Character.isWhitespace(character)) {
        return 0;
      }
    }

    return 0;
  }

}
