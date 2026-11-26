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

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Детектор вхождений заданных слов/фраз в строку.
 * <p>
 * Подсчитывает количество вхождений предопределенных слов и фраз,
 * характерных для кода BSL.
 */
public class ContainsDetector extends AbstractDetector {

  private final List<String> searchWords;

  /**
   * Создать детектор вхождений.
   *
   * @param probability Вероятность обнаружения
   * @param searchWords Массив слов/фраз для поиска
   */
  public ContainsDetector(double probability, String... searchWords) {
    super(probability);
    this.searchWords = Arrays.asList(searchWords);
  }

  @Override
  public int scan(String line) {
    String lineWithoutWhitespaces = StringUtils.deleteWhitespace(line);
    int matchers = 0;
    for (String str : searchWords) {
      matchers += StringUtils.countMatches(lineWithoutWhitespaces, str);
    }
    return matchers;
  }

}
