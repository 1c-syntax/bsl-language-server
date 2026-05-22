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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;

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
   * Выбрать индекс варианта сигнатуры по типам фактических аргументов.
   * <p>
   * Для каждой подходящей по arity сигнатуры считается score: +1 за каждый
   * аргумент, тип которого пересекается (по qualifiedName, регистронезависимо)
   * с задекларированными типами параметра; +0 если тип аргумента или тип
   * параметра неизвестен (`TypeSet.EMPTY`) — нейтральный фолбэк. Выбирается
   * сигнатура с максимальным score; при равенстве — наименьший индекс
   * (как и {@link #pickIndexByArity}). Это даёт правильный pick для
   * перегрузок вида:
   * <pre>
   * ТЗ.Скопировать(Строки: Массив, Колонки: Строка);
   * ТЗ.Скопировать(ПараметрыОтбора: Структура, Колонки: Строка);
   * </pre>
   * — по типу первого аргумента (Массив или Структура) выбирается
   * соответствующий вариант.
   *
   * @param signatures упорядоченный список вариантов
   * @param argTypes   типы фактических аргументов в порядке вызова; для
   *                   неизвестного типа — {@link TypeSet#EMPTY}.
   * @return индекс подходящего варианта; {@code -1}, если ни один не
   *         удовлетворяет arity. Не учитывает union типов параметра как
   *         положительный сигнал, если ни одна из его опций не пересекается
   *         с типом аргумента.
   */
  public static int pickIndexByTypes(List<SignatureDescriptor> signatures, List<TypeSet> argTypes) {
    if (signatures.isEmpty()) {
      return -1;
    }
    var argCount = argTypes.size();
    int bestIndex = -1;
    int bestScore = Integer.MIN_VALUE;
    for (int i = 0; i < signatures.size(); i++) {
      var sig = signatures.get(i);
      if (!acceptsArity(sig, argCount)) {
        continue;
      }
      int score = scoreByTypes(sig, argTypes);
      if (score > bestScore) {
        bestScore = score;
        bestIndex = i;
      }
    }
    if (bestIndex >= 0) {
      return bestIndex;
    }
    // Никто не прошёл по arity — fallback к чисто arity-based pick.
    return pickIndexByArity(signatures, argCount);
  }

  /**
   * Сигнатура принимает {@code argCount} аргументов, если {@code argCount}
   * попадает в диапазон {@code [required, total]} либо последний параметр
   * variadic (имя содержит {@code ",..."}) — тогда верхняя граница неограничена.
   */
  private static boolean acceptsArity(SignatureDescriptor sig, int argCount) {
    var params = sig.parameters();
    int total = params.size();
    int required = 0;
    for (var p : params) {
      if (!p.optional()) {
        required++;
      }
    }
    if (argCount >= required && argCount <= total) {
      return true;
    }
    if (!params.isEmpty()) {
      var last = params.get(params.size() - 1);
      if (last.name() != null && last.name().contains(",...,") && argCount >= total - 1) {
        return true;
      }
    }
    return false;
  }

  /**
   * Считает количество аргументов, чьи типы СОВПАДАЮТ с задекларированными
   * типами соответствующего параметра. Аргументы с неизвестным типом и
   * параметры без объявленных типов — нейтральны (не добавляют и не
   * вычитают score).
   */
  private static int scoreByTypes(SignatureDescriptor sig, List<TypeSet> argTypes) {
    int score = 0;
    var params = sig.parameters();
    if (params.isEmpty() || argTypes == null || argTypes.isEmpty()) {
      return 0;
    }
    int n = Math.min(params.size(), argTypes.size());
    for (int i = 0; i < n; i++) {
      var argType = argTypes.get(i);
      var paramType = params.get(i).types();
      if (argType == null || argType.isEmpty()) {
        continue;
      }
      if (paramType == null || paramType.isEmpty()) {
        continue;
      }
      if (typesIntersect(argType, paramType)) {
        score++;
      } else {
        // Заявленный тип параметра несовместим с типом аргумента — это
        // сильный негативный сигнал.
        score--;
      }
    }
    // Если последний параметр variadic, считаем что «лишние» аргументы
    // matches на нём с типом последнего параметра.
    if (argTypes.size() > params.size()) {
      var last = params.get(params.size() - 1);
      if (last.name() != null && last.name().contains(",...,")) {
        var paramType = last.types();
        for (int i = params.size(); i < argTypes.size(); i++) {
          var argType = argTypes.get(i);
          if (argType == null || argType.isEmpty() || paramType == null || paramType.isEmpty()) {
            continue;
          }
          if (typesIntersect(argType, paramType)) {
            score++;
          } else {
            score--;
          }
        }
      }
    }
    return score;
  }

  /**
   * Регистронезависимое пересечение двух {@link TypeSet} по {@code qualifiedName}.
   */
  private static boolean typesIntersect(TypeSet a, TypeSet b) {
    for (var refA : a.refs()) {
      for (var refB : b.refs()) {
        if (refA.qualifiedName().equalsIgnoreCase(refB.qualifiedName())) {
          return true;
        }
        if (refA.equals(TypeRef.ANY) || refB.equals(TypeRef.ANY)) {
          return true;
        }
      }
    }
    return false;
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
