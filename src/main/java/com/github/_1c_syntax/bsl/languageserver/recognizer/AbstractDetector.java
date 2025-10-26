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
package com.github._1c_syntax.bsl.languageserver.recognizer;

/**
 * Абстрактный детектор для распознавания кода.
 * <p>
 * Базовый класс для различных детекторов, определяющих
 * вероятность того, что строка является кодом BSL.
 */
public abstract class AbstractDetector {
  private final double probability;

  /**
   * Создать детектор с заданной вероятностью.
   *
   * @param probability Вероятность обнаружения (от 0 до 1)
   */
  public AbstractDetector(double probability) {
    if (probability < 0 || probability > 1) {
      throw new IllegalArgumentException("probability should be between [0 .. 1]");
    }
    this.probability = probability;
  }

  /**
   * Сканировать строку на предмет совпадений.
   *
   * @param line Строка для сканирования
   * @return Количество найденных совпадений
   */
  abstract int scan(String line);

  /**
   * Определить вероятность того, что строка является кодом.
   *
   * @param line Строка для анализа
   * @return Вероятность (от 0 до 1)
   */
  final double detect(String line) {
    int matchers = scan(line);
    if (matchers == 0) {
      return 0;
    }
    return 1 - Math.pow(1 - probability, matchers);
  }
}
