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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище обращений к символам.
 */
@Component
@WorkspaceScope
public class SymbolOccurrenceRepository {

  /**
   * Обращения к символам в разрезе символов.
   * <p>
   * Значение карты хранится в компактном виде: для символа с единственным обращением —
   * сам {@link SymbolOccurrence} без обёртки-коллекции (доминирующий случай), для символа
   * с несколькими обращениями — отсортированный по {@link SymbolOccurrence#compareTo} массив
   * {@code SymbolOccurrence[]}. Это устраняет накладные расходы {@link java.util.concurrent.ConcurrentSkipListSet}
   * (заголовок мапы 88Б, sentinel- и index-узлы), которые на больших проектах составляли сотни МБ.
   * <p>
   * Все изменения выполняются атомарно по ключу через {@link ConcurrentHashMap#compute}
   * и {@link ConcurrentHashMap#computeIfPresent}; массив неизменяем после публикации,
   * поэтому читатели ({@link #getAllBySymbol}) всегда видят согласованный снимок.
   */
  private final Map<Symbol, Object> occurrencesToSymbols = new ConcurrentHashMap<>();

  /**
   * Сохранить обращение к символу в хранилище.
   *
   * @param symbolOccurrence Обращение к символу.
   */
  public void save(SymbolOccurrence symbolOccurrence) {
    occurrencesToSymbols.compute(symbolOccurrence.symbol(), (symbol, current) -> insert(current, symbolOccurrence));
  }

  /**
   * Получить все обращения к указанному символу.
   *
   * @param symbol Символ.
   * @return Список обращений к символу (в порядке сортировки {@link SymbolOccurrence}).
   */
  public Set<SymbolOccurrence> getAllBySymbol(Symbol symbol) {
    var current = occurrencesToSymbols.get(symbol);
    if (current == null) {
      return Collections.emptySet();
    }
    if (current instanceof SymbolOccurrence single) {
      return Collections.singleton(single);
    }
    var array = (SymbolOccurrence[]) current;
    var result = new LinkedHashSet<SymbolOccurrence>(array.length);
    Collections.addAll(result, array);
    return Collections.unmodifiableSet(result);
  }

  /**
   * Удалить сохраненные данные по указанным обращениям к символу.
   *
   * @param symbolOccurrences Список обращений к символам.
   */
  public void deleteAll(Set<SymbolOccurrence> symbolOccurrences) {
    symbolOccurrences.forEach(symbolOccurrence ->
      occurrencesToSymbols.computeIfPresent(symbolOccurrence.symbol(), (symbol, current) -> remove(current, symbolOccurrence))
    );
  }

  /**
   * Вставить обращение в текущее значение, сохранив сортировку и семантику множества
   * (дубликат по {@code compareTo == 0} игнорируется).
   */
  private static Object insert(Object current, SymbolOccurrence occurrence) {
    if (current == null) {
      return occurrence;
    }
    if (current instanceof SymbolOccurrence single) {
      int c = occurrence.compareTo(single);
      if (c == 0) {
        return single;
      }
      return c < 0
        ? new SymbolOccurrence[]{occurrence, single}
        : new SymbolOccurrence[]{single, occurrence};
    }
    var array = (SymbolOccurrence[]) current;
    int idx = Arrays.binarySearch(array, occurrence);
    if (idx >= 0) {
      return array; // элемент с таким же ключом уже есть
    }
    int insertPos = -idx - 1;
    var updated = new SymbolOccurrence[array.length + 1];
    System.arraycopy(array, 0, updated, 0, insertPos);
    updated[insertPos] = occurrence;
    System.arraycopy(array, insertPos, updated, insertPos + 1, array.length - insertPos);
    return updated;
  }

  /**
   * Удалить обращение из текущего значения, схлопывая массив обратно к одиночному
   * значению или {@code null} (удаление ключа) по мере уменьшения.
   */
  private static Object remove(Object current, SymbolOccurrence occurrence) {
    if (current instanceof SymbolOccurrence single) {
      return single.compareTo(occurrence) == 0 ? null : single;
    }
    var array = (SymbolOccurrence[]) current;
    int idx = Arrays.binarySearch(array, occurrence);
    if (idx < 0) {
      return array;
    }
    if (array.length == 2) {
      return array[idx == 0 ? 1 : 0]; // схлопнуть к одиночному значению
    }
    var updated = new SymbolOccurrence[array.length - 1];
    System.arraycopy(array, 0, updated, 0, idx);
    System.arraycopy(array, idx + 1, updated, idx, array.length - idx - 1);
    return updated;
  }
}
