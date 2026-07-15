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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище обращений к символам.
 */
@Component
@WorkspaceScope
public class SymbolOccurrenceRepository {

  private static final SymbolOccurrence[] EMPTY = new SymbolOccurrence[0];

  /**
   * Обращения к символам в разрезе символов.
   * <p>
   * Значение карты хранится в компактном виде: для символа с единственным обращением —
   * сам {@link SymbolOccurrence} без обёртки-коллекции (доминирующий случай); для символа
   * с несколькими обращениями — отсортированный по {@link SymbolOccurrence#compareTo} массив
   * {@code SymbolOccurrence[]}. Массивы вместо {@code ConcurrentSkipListSet} на каждый символ
   * убирают сотни МБ накладных расходов (на cpm — ~565 МиБ).
   * <p>
   * Обращения символа наполняются пачкой на документ ({@link #saveAll}) — одно копирование
   * массива на документ вместо копирования на каждое обращение, что снимает квадратичность
   * построения на «хабах» без перехода в скип-лист. Все изменения атомарны по ключу через
   * {@link ConcurrentHashMap#compute}/{@link ConcurrentHashMap#computeIfPresent}; массив
   * неизменяем после публикации, поэтому читатели ({@link #getAllBySymbol}) видят согласованный
   * снимок.
   */
  private final Map<Symbol, Object> occurrencesToSymbols = new ConcurrentHashMap<>();

  /**
   * Сохранить одиночное обращение к символу.
   *
   * @param symbolOccurrence Обращение к символу.
   */
  public void save(SymbolOccurrence symbolOccurrence) {
    occurrencesToSymbols.compute(symbolOccurrence.symbol(), (symbol, current) -> insert(current, symbolOccurrence));
  }

  /**
   * Пакетно сохранить обращения ОДНОГО символа (все его обращения из одного документа) —
   * основной путь записи. Пачка сливается с текущим значением за одно копирование
   * ({@code O(n + k)}), а не по копированию массива на каждое обращение: это снимает
   * внутрифайловую квадратичность, а на реальном cpm — и накопление на хабах (≈14× меньше
   * копирований, чем по одному).
   *
   * @param symbol      Символ.
   * @param occurrences Обращения к символу из одного документа.
   */
  public void saveAll(Symbol symbol, Collection<SymbolOccurrence> occurrences) {
    if (occurrences.isEmpty()) {
      return;
    }
    occurrencesToSymbols.compute(symbol, (key, current) -> insertAll(current, occurrences));
  }

  /**
   * Получить все обращения к указанному символу.
   *
   * @param symbol Символ.
   * @return Обращения к символу в порядке сортировки {@link SymbolOccurrence},
   *   представление без копирования.
   */
  public Collection<SymbolOccurrence> getAllBySymbol(Symbol symbol) {
    var current = occurrencesToSymbols.get(symbol);
    if (current == null) {
      return List.of();
    }
    if (current instanceof SymbolOccurrence single) {
      return List.of(single);
    }
    return Collections.unmodifiableList(Arrays.asList((SymbolOccurrence[]) current));
  }

  /**
   * Удалить сохраненные данные по указанным обращениям к символу.
   *
   * @param symbolOccurrences Список обращений к символам.
   */
  public void deleteAll(Collection<SymbolOccurrence> symbolOccurrences) {
    // группируем по символу и удаляем пачку за один проход — симметрично saveAll,
    // иначе удаление нескольких обращений одного (возможно, крупного) символа
    // пересобирало бы его массив на каждое обращение.
    var toRemoveBySymbol = new HashMap<Symbol, Set<SymbolOccurrence>>();
    symbolOccurrences.forEach(occurrence ->
      toRemoveBySymbol.computeIfAbsent(occurrence.symbol(), symbol -> new HashSet<>()).add(occurrence));
    toRemoveBySymbol.forEach((symbol, toRemove) ->
      occurrencesToSymbols.computeIfPresent(symbol, (key, current) -> removeAll(current, toRemove)));
  }

  private static Object insert(@Nullable Object current, SymbolOccurrence occurrence) {
    if (current == null) {
      return occurrence;
    }
    if (current instanceof SymbolOccurrence single) {
      return insertIntoSingle(single, occurrence);
    }
    return insertIntoArray((SymbolOccurrence[]) current, occurrence);
  }

  private static Object insertIntoSingle(SymbolOccurrence single, SymbolOccurrence occurrence) {
    int compare = occurrence.compareTo(single);
    if (compare == 0) {
      return single;
    }
    return compare < 0
      ? new SymbolOccurrence[]{occurrence, single}
      : new SymbolOccurrence[]{single, occurrence};
  }

  private static Object insertIntoArray(SymbolOccurrence[] array, SymbolOccurrence occurrence) {
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

  private static Object insertAll(@Nullable Object current, Collection<SymbolOccurrence> occurrences) {
    if (occurrences.size() == 1) {
      // мелкая пачка (на cpm ~45% пачек — по одному) — без накладных расходов stream
      return insert(current, occurrences.iterator().next());
    }
    var added = occurrences.stream().distinct().sorted().toArray(SymbolOccurrence[]::new);
    SymbolOccurrence[] base;
    if (current == null) {
      base = EMPTY;
    } else if (current instanceof SymbolOccurrence single) {
      base = new SymbolOccurrence[]{single};
    } else {
      base = (SymbolOccurrence[]) current;
    }
    var merged = mergeSorted(base, added);
    return merged.length == 1 ? merged[0] : merged;
  }

  /**
   * Влить отсортированную пачку {@code added} в отсортированный {@code base} без дубликатов
   * ({@code compareTo == 0} — один элемент). Участки {@code base} между позициями вставки
   * копируются пачками через {@link System#arraycopy} (дёшево), а позиция каждого элемента
   * пачки ищется бинарным поиском — {@code O(k·log n)} сравнений вместо {@code O(n)}, что
   * важно при пачке, малой относительно {@code base}.
   */
  private static SymbolOccurrence[] mergeSorted(SymbolOccurrence[] base, SymbolOccurrence[] added) {
    var merged = new SymbolOccurrence[base.length + added.length];
    int baseFrom = 0;
    int p = 0;
    for (var occurrence : added) {
      int idx = Arrays.binarySearch(base, baseFrom, base.length, occurrence);
      int insertPos = idx >= 0 ? idx : -idx - 1;
      int run = insertPos - baseFrom;
      System.arraycopy(base, baseFrom, merged, p, run);
      p += run;
      baseFrom = insertPos;
      if (idx < 0) {
        merged[p++] = occurrence; // новый элемент; дубликат (idx >= 0) пропускаем
      }
    }
    int tail = base.length - baseFrom;
    System.arraycopy(base, baseFrom, merged, p, tail);
    p += tail;
    return p == merged.length ? merged : Arrays.copyOf(merged, p);
  }

  /**
   * Удалить пачку обращений из текущего значения за один проход. Для массива —
   * один фильтрующий проход {@code O(n)} вместо копирования на каждый элемент пачки;
   * схлопывается к одиночному значению или {@code null} (удаление ключа).
   */
  private static @Nullable Object removeAll(Object current, Set<SymbolOccurrence> toRemove) {
    if (current instanceof SymbolOccurrence single) {
      return toRemove.contains(single) ? null : single;
    }
    var kept = Arrays.stream((SymbolOccurrence[]) current)
      .filter(occurrence -> !toRemove.contains(occurrence))
      .toArray(SymbolOccurrence[]::new);
    if (kept.length == 0) {
      return null;
    }
    return kept.length == 1 ? kept[0] : kept;
  }
}
