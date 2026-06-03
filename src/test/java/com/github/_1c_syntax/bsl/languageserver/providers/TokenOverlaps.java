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
import java.util.List;

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
   * Находит пересечения токенов на одной строке.
   *
   * @param entries собранные со всех сапплаеров токены
   * @return список пар пересекающихся токенов; пустой, если конфликтов нет
   */
  static List<TokenOverlap> findOverlaps(List<SemanticTokenEntry> entries) {
    var byLine = new HashMap<Integer, List<SemanticTokenEntry>>();
    for (var entry : entries) {
      byLine.computeIfAbsent(entry.line(), k -> new ArrayList<>()).add(entry);
    }

    var overlaps = new ArrayList<TokenOverlap>();
    for (var lineTokens : byLine.values()) {
      lineTokens.sort(Comparator.comparingInt(SemanticTokenEntry::start));
      for (int i = 0; i < lineTokens.size(); i++) {
        var a = lineTokens.get(i);
        int aEnd = a.start() + a.length();
        for (int j = i + 1; j < lineTokens.size(); j++) {
          var b = lineTokens.get(j);
          if (b.start() >= aEnd) {
            break; // токены отсортированы по start — дальше пересечений с a нет
          }
          // a и b уже на одной строке, поэтому equals рекорда (все 5 полей)
          // эквивалентен проверке «точный дубль»: одинаковые позиция и оформление.
          if (!a.equals(b)) {
            overlaps.add(new TokenOverlap(a, b));
          }
        }
      }
    }
    return overlaps;
  }
}
