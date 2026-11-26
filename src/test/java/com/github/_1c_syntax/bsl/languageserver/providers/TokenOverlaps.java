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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokenEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Тестовая утилита: детект пересечений семантических токенов, выданных
 * сапплаерами на одном участке кода.
 * <p>
 * По спецификации LSP токены семантической подсветки не должны пересекаться:
 * если два сапплаера покрасили один и тот же (или перекрывающийся) участок
 * разными {@code type}/{@code modifiers} — это конфликт подсветки, который
 * клиент отрисует недетерминированно. Точные дубли (полностью совпадающие
 * токены от разных сапплаеров) конфликтом не считаются — их убирает
 * де-дупликация в {@code SemanticTokensProvider}.
 * <p>
 * Чисто отладочный/регрессионный инструмент, поэтому живёт в тестовом
 * source-set, а не в продакшен-провайдере.
 */
final class TokenOverlaps {

  private TokenOverlaps() {
  }

  /**
   * Пересечение двух токенов на одном участке кода.
   *
   * @param first  токен с меньшей (или равной) начальной позицией
   * @param second пересекающийся с ним токен
   */
  record TokenOverlap(SemanticTokenEntry first, SemanticTokenEntry second) {
  }

  /**
   * Часть токена в пределах одной строки: токен может быть многострочным
   * (склеенные комментарии и т.п.), тогда он раскладывается на несколько спанов.
   */
  private record Span(int line, int start, int end, SemanticTokenEntry origin) {
  }

  /**
   * Находит пересечения токенов, считая каждый токен однострочным.
   * Подходит для синтетических токенов в юнит-тестах.
   *
   * @param entries собранные со всех сапплаеров токены
   * @return список пар пересекающихся токенов; пустой, если конфликтов нет
   */
  static List<TokenOverlap> findOverlaps(List<SemanticTokenEntry> entries) {
    return findOverlaps(entries, line -> Integer.MAX_VALUE);
  }

  /**
   * Находит пересечения токенов с учётом многострочных токенов: длина токена,
   * выходящая за конец строки, переносится на следующие строки (по длинам строк
   * из {@code lineLength}). Без этого многострочный токен сравнивался бы только
   * на своей стартовой строке, и пересечение на строке-продолжении терялось бы.
   *
   * @param entries    собранные со всех сапплаеров токены
   * @param lineLength длина строки по её 0-индексу (число символов без перевода строки)
   * @return список пар пересекающихся токенов; пустой, если конфликтов нет
   */
  static List<TokenOverlap> findOverlaps(List<SemanticTokenEntry> entries, IntUnaryOperator lineLength) {
    var spansByLine = new HashMap<Integer, List<Span>>();
    for (var entry : entries) {
      expand(entry, lineLength, spansByLine);
    }

    var overlaps = new ArrayList<TokenOverlap>();
    var reported = new HashSet<Set<SemanticTokenEntry>>();
    for (var spans : spansByLine.values()) {
      spans.sort(Comparator.comparingInt(Span::start));
      for (int i = 0; i < spans.size(); i++) {
        var a = spans.get(i);
        for (int j = i + 1; j < spans.size(); j++) {
          var b = spans.get(j);
          if (b.start() >= a.end()) {
            break; // спаны отсортированы по start — дальше пересечений с a нет
          }
          if (a.origin().equals(b.origin())) {
            continue; // точный дубль токена — не конфликт
          }
          // пара токенов может пересекаться на нескольких строках — рапортуем один раз.
          if (reported.add(Set.of(a.origin(), b.origin()))) {
            overlaps.add(new TokenOverlap(a.origin(), b.origin()));
          }
        }
      }
    }
    return overlaps;
  }

  /** Разложить токен на пер-строчные спаны, перенося длину за концом строки. */
  private static void expand(SemanticTokenEntry entry, IntUnaryOperator lineLength,
                             Map<Integer, List<Span>> spansByLine) {
    int line = entry.line();
    int col = entry.start();
    int remaining = entry.length();
    while (remaining > 0) {
      int available = Math.max(0, lineLength.applyAsInt(line) - col);
      int take = Math.min(remaining, available);
      if (take > 0) {
        spansByLine.computeIfAbsent(line, k -> new ArrayList<>())
          .add(new Span(line, col, col + take, entry));
      }
      if (remaining <= available) {
        break;
      }
      remaining -= available + 1; // +1 — символ перевода строки
      line++;
      col = 0;
    }
  }
}
