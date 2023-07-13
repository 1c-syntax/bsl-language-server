/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.references.model;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Хранилище обращений к символам.
 */
@Component
public class SymbolOccurrenceRepository {

  /**
   * Список обращений к символам в разрезе символов.
   */
  private final Map<Symbol, Set<SymbolOccurrence>> occurrencesToSymbols = new ConcurrentSkipListMap<>();

  /**
   * Сохранить обращение к символу в хранилище.
   *
   * @param symbolOccurrence Обращение к символу.
   */
  public void save(SymbolOccurrence symbolOccurrence) {
    occurrencesToSymbols.computeIfAbsent(symbolOccurrence.getSymbol(), symbol -> new ConcurrentSkipListSet<>())
      .add(symbolOccurrence);
  }

  /**
   * Получить все обращения к указанному символу.
   *
   * @param symbol Символ.
   * @return Список обращений к символу.
   */
  public Set<SymbolOccurrence> getAllBySymbol(Symbol symbol) {
    return occurrencesToSymbols.getOrDefault(symbol, Collections.emptySet());
  }

  /**
   * Удалить сохраненные данные по указанным обращениям к символу.
   *
   * @param symbolOccurrences Список обращений к символам.
   */
  public void deleteAll(Set<SymbolOccurrence> symbolOccurrences) {
    symbolOccurrences.forEach(symbolOccurrence ->
      occurrencesToSymbols.get(symbolOccurrence.getSymbol()).remove(symbolOccurrence)
    );
  }

}
