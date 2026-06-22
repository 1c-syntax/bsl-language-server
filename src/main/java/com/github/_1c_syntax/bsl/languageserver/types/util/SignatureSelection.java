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

import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
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
    if (argCount < 0) {
      return -1;
    }
    for (var i = 0; i < signatures.size(); i++) {
      if (acceptsArity(signatures.get(i), argCount)) {
        return i;
      }
    }
    return -1;
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
   * вариадик ({@code ParameterDescriptor.variadic()}) — тогда верхняя граница
   * неограничена.
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
      // Вариадик-хвост снимает верхнюю границу arity, но нижняя остаётся
      // required (обязательный вариадик-параметр, например Макс(Значение…),
      // требует хотя бы одного значения).
      return params.getLast().variadic() && argCount >= required;
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
    var params = sig.parameters();
    if (params.isEmpty() || argTypes.isEmpty()) {
      return 0;
    }
    var score = 0;
    var n = Math.min(params.size(), argTypes.size());
    for (var i = 0; i < n; i++) {
      score += scoreOne(argTypes.get(i), params.get(i).types());
    }
    return score + scoreVariadicTail(params, argTypes);
  }

  /**
   * Скоринг одного аргумента: {@code +1} при пересечении типов, {@code -1} при
   * несовместимости (сильный негативный сигнал), {@code 0} если тип аргумента
   * или параметра неизвестен.
   */
  private static int scoreOne(TypeSet argType, TypeSet paramType) {
    if (argType.isEmpty() || paramType.isEmpty()) {
      return 0;
    }
    return typesIntersect(argType, paramType) ? 1 : -1;
  }

  /**
   * Доскоринг «лишних» аргументов (сверх числа параметров) на типе вариадик-хвоста.
   * {@code 0}, если хвост не вариадик или лишних аргументов нет.
   */
  private static int scoreVariadicTail(List<ParameterDescriptor> params, List<TypeSet> argTypes) {
    if (argTypes.size() <= params.size() || !params.getLast().variadic()) {
      return 0;
    }
    var paramType = params.getLast().types();
    var score = 0;
    for (var i = params.size(); i < argTypes.size(); i++) {
      score += scoreOne(argTypes.get(i), paramType);
    }
    return score;
  }

  /**
   * Регистронезависимое пересечение двух {@link TypeSet} по {@code qualifiedName}.
   * Универсальный тип ({@code Произвольный}/{@code Arbitrary}/{@link TypeRef#ANY}) —
   * вершина решётки типов и совместим с любым: параметр такого типа не должен
   * штрафовать аргумент произвольного типа при выборе перегрузки.
   */
  private static boolean typesIntersect(TypeSet a, TypeSet b) {
    for (var refA : a.refs()) {
      for (var refB : b.refs()) {
        if (refA.qualifiedName().equalsIgnoreCase(refB.qualifiedName())) {
          return true;
        }
        if (isAny(refA) || isAny(refB)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Является ли {@code ref} универсальным типом — вершиной решётки. Имя
   * {@code Произвольный}/{@code Arbitrary} канонизируется в {@link TypeRef#ANY}
   * ещё при создании {@link TypeRef}, поэтому достаточно сравнения с {@link TypeRef#ANY}.
   */
  private static boolean isAny(TypeRef ref) {
    return ref.equals(TypeRef.ANY);
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
