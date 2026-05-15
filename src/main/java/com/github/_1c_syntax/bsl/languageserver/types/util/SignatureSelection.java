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
package com.github._1c_syntax.bsl.languageserver.types.util;

import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;

import java.util.List;

/**
 * Утилиты выбора подходящего варианта сигнатуры из перегрузок метода/конструктора.
 * <p>
 * Используется в hover (показать описание варианта, соответствующего
 * фактическим аргументам), signature help (выбрать {@code activeSignature}),
 * completion ({@code detail}).
 */
public final class SignatureSelection {

  private SignatureSelection() {
  }

  /**
   * Выбрать индекс варианта сигнатуры, соответствующего количеству фактически
   * переданных аргументов.
   *
   * @param signatures упорядоченный список вариантов
   * @param argCount   количество фактически переданных аргументов (≥0)
   * @return индекс подходящего варианта, либо {@code -1} если нет ни одного,
   * у которого {@code required ≤ argCount ≤ total}
   */
  public static int pickIndexByArity(List<SignatureDescriptor> signatures, int argCount) {
    if (signatures.isEmpty() || argCount < 0) {
      return -1;
    }
    int fallback = -1;
    for (int i = 0; i < signatures.size(); i++) {
      var sig = signatures.get(i);
      int total = sig.parameters().size();
      int required = (int) sig.parameters().stream().filter(p -> !p.optional()).count();
      if (argCount >= required && argCount <= total) {
        return i;
      }
      if (fallback == -1 && total == argCount) {
        fallback = i;
      }
    }
    return fallback;
  }

  /**
   * Выбрать индекс варианта сигнатуры для signature help, когда пользователь
   * находится на параметре с индексом {@code activeParameter}.
   * <p>
   * Предпочитается наименьший вариант, у которого {@code activeParameter < total}.
   * Если ни один не подходит — берём вариант с наибольшим числом параметров.
   *
   * @param signatures      упорядоченный список вариантов
   * @param activeParameter индекс параметра под курсором (0-based)
   * @return индекс «активного» варианта; {@code 0} для пустого {@code signatures}
   */
  public static int pickIndexByActiveParameter(List<SignatureDescriptor> signatures, int activeParameter) {
    if (signatures.isEmpty()) {
      return 0;
    }
    int bestFit = -1;
    int bestFitTotal = Integer.MAX_VALUE;
    int largest = 0;
    int largestTotal = -1;
    for (int i = 0; i < signatures.size(); i++) {
      int total = signatures.get(i).parameters().size();
      if (activeParameter < total && total < bestFitTotal) {
        bestFit = i;
        bestFitTotal = total;
      }
      if (total > largestTotal) {
        largest = i;
        largestTotal = total;
      }
    }
    return bestFit >= 0 ? bestFit : largest;
  }
}
