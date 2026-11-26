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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Нечёткое (fuzzy) сопоставление имени с запросом: непрерывная подстрока в любом
 * месте имени и разбросанная подпоследовательность, со скорингом качества совпадения.
 * <p>
 * Скор — целое, где <b>меньшее значение релевантнее</b>. Лестница совпадений:
 * точное имя ({@link #SCORE_EXACT}) → префикс полного имени ({@link #SCORE_PREFIX}) →
 * непрерывная подстрока в середине имени ({@link #SCORE_SUBSTRING}) →
 * подпоследовательность ({@link #SCORE_SUBSEQUENCE}{@code  + позиция первого совпавшего символа}:
 * более раннее начало совпадения релевантнее). Несовпадение — {@link #NO_MATCH}.
 * <p>
 * Метод {@link #fuzzyScore(String, String)} (только подстрока/подпоследовательность) переиспользуется
 * как «грязный» fuzzy-хвост поиска по символам воркспейса; {@link #score(String, String)} добавляет
 * сверху точное/префиксное совпадение и применяется в автодополнении.
 */
@Component
public class FuzzyMatcher {

  /**
   * Совпадения нет.
   */
  public static final int NO_MATCH = -1;

  /**
   * Скор точного совпадения имени с запросом (наиболее релевантно).
   */
  public static final int SCORE_EXACT = 0;

  /**
   * Скор совпадения полного имени по префиксу запроса.
   */
  public static final int SCORE_PREFIX = 1;

  /**
   * Скор совпадения запроса как непрерывной подстроки имени (но не префикса имени).
   */
  public static final int SCORE_SUBSTRING = 5;

  /**
   * Базовый скор совпадения запроса как подпоследовательности имени; к нему прибавляется
   * позиция первого совпавшего символа (более ранняя позиция — релевантнее).
   */
  public static final int SCORE_SUBSEQUENCE = 6;

  /**
   * Скор совпадения имени с запросом: точное / префиксное / подстрока / подпоследовательность.
   * <p>
   * Имя приводится к нижнему регистру внутри метода ({@link Locale#ROOT}); запрос должен быть
   * передан уже в нижнем регистре и непустым.
   *
   * @param name       имя-кандидат (в исходном регистре)
   * @param lowerQuery запрос в нижнем регистре, непустой
   * @return скор {@code >= SCORE_EXACT} (меньше — релевантнее), либо {@link #NO_MATCH}, если совпадения нет
   */
  public int score(String name, String lowerQuery) {
    var lowerName = name.toLowerCase(Locale.ROOT);
    if (lowerName.equals(lowerQuery)) {
      return SCORE_EXACT;
    }
    if (lowerName.startsWith(lowerQuery)) {
      return SCORE_PREFIX;
    }
    return fuzzyScore(lowerName, lowerQuery);
  }

  /**
   * Совпадает ли имя с запросом хотя бы как подпоследовательность (любой уровень лестницы).
   *
   * @param name       имя-кандидат (в исходном регистре)
   * @param lowerQuery запрос в нижнем регистре, непустой
   * @return {@code true}, если совпадение есть
   */
  public boolean matches(String name, String lowerQuery) {
    return score(name, lowerQuery) != NO_MATCH;
  }

  /**
   * Скор «не-префиксного» fuzzy-совпадения lowercase-имени с lowercase-запросом:
   * непрерывная подстрока ({@link #SCORE_SUBSTRING}) либо подпоследовательность
   * ({@link #SCORE_SUBSEQUENCE}{@code  + позиция первого совпавшего символа}).
   * <p>
   * Оба аргумента должны быть уже в нижнем регистре (приведение — забота вызывающего, у которого
   * lowercase-имя может быть предвычислено).
   *
   * @param lowerName  lowercase-имя кандидата
   * @param lowerQuery lowercase-запрос, непустой
   * @return скор {@code >= SCORE_SUBSTRING}, либо {@link #NO_MATCH}, если совпадения нет
   */
  public int fuzzyScore(String lowerName, String lowerQuery) {
    if (lowerName.contains(lowerQuery)) {
      return SCORE_SUBSTRING;
    }
    var firstMatch = subsequenceFirstIndex(lowerName, lowerQuery);
    if (firstMatch >= 0) {
      return SCORE_SUBSEQUENCE + firstMatch;
    }
    return NO_MATCH;
  }

  /**
   * Проверить, что {@code lowerQuery} — подпоследовательность {@code lowerName}, и вернуть индекс
   * символа имени, на котором совпал первый символ запроса.
   *
   * @param lowerName  lowercase-имя кандидата
   * @param lowerQuery lowercase-запрос, непустой
   * @return индекс первого совпавшего символа, либо {@code -1}, если не подпоследовательность
   */
  private static int subsequenceFirstIndex(String lowerName, String lowerQuery) {
    var firstMatch = -1;
    var queryIndex = 0;
    for (var nameIndex = 0; nameIndex < lowerName.length() && queryIndex < lowerQuery.length(); nameIndex++) {
      if (lowerName.charAt(nameIndex) == lowerQuery.charAt(queryIndex)) {
        if (queryIndex == 0) {
          firstMatch = nameIndex;
        }
        queryIndex++;
      }
    }
    return queryIndex == lowerQuery.length() ? firstMatch : -1;
  }
}
