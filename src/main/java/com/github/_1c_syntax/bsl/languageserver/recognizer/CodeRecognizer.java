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
 * Распознаватель кода BSL.
 * <p>
 * Определяет, является ли строка кода BSL-кодом,
 * используя набор детекторов и порог вероятности.
 */
public class CodeRecognizer {
  private final LanguageFootprint language;
  private final double threshold;

  /**
   * Создать распознаватель кода.
   *
   * @param threshold Порог вероятности для распознавания (от 0 до 1)
   * @param language Отпечаток языка с набором детекторов
   */
  public CodeRecognizer(double threshold, LanguageFootprint language) {
    this.language = language;
    this.threshold = threshold;
  }

  private double recognition(String line) {
    double probability = 0;
    for (AbstractDetector pattern : language.getDetectors()) {
      probability = 1 - (1 - probability) * (1 - pattern.detect(line));
    }
    return probability;
  }

  /**
   * Проверить, соответствует ли строка условию распознавания как код BSL.
   *
   * @param line Строка для проверки
   * @return true, если строка распознана как код BSL
   */
  public final boolean meetsCondition(String line) {
    return recognition(line) - threshold > 0;
  }
}
