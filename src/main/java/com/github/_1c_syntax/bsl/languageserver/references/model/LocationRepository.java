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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Хранилище расположений обращений к символам.
 */
@Component
@WorkspaceScope
public class LocationRepository {

  private static final SymbolOccurrence[] EMPTY_OCCURRENCES = new SymbolOccurrence[0];

  /**
   * Порядок вхождений по началу диапазона. Диапазоны вхождений непересекающиеся,
   * поэтому порядок тотален и однозначен.
   */
  private static final Comparator<SymbolOccurrence> BY_RANGE_START = Comparator
    .comparingInt((SymbolOccurrence occurrence) -> occurrence.location().startLine())
    .thenComparingInt(occurrence -> occurrence.location().startCharacter());

  /**
   * Вхождения документа, сгруппированные по URI.
   * <p>
   * Значение — неизменяемый массив {@code SymbolOccurrence[]}, а не concurrent-множество:
   * вхождения одного документа пишутся одним потоком (перестроение документа сериализовано),
   * а полный набор заменяется атомарным swap-ом ссылки ({@link #replaceOccurrences}), поэтому
   * конкурентные читатели всегда видят согласованный снимок. Массив вместо
   * {@code ConcurrentHashMap.newKeySet()} убирает ~1 Node на каждое вхождение (на больших
   * проектах — миллионы узлов и сотни МБ).
   */
  private final Map<URI, SymbolOccurrence[]> locations = new ConcurrentHashMap<>();

  /**
   * Вторичный индекс для {@link #findByPosition}: отсортированный по началу
   * диапазона снимок вхождений документа. Позволяет искать вхождение в позиции
   * бинарным поиском без построения карты «строка → множество вхождений»
   * (карта с per-строчными множествами на больших проектах доминировала в
   * потреблении памяти индексом ссылок).
   * <p>
   * Снимок ленивый: сбрасывается при любом изменении вхождений URI
   * ({@link #replaceOccurrences}, {@link #updateLocation}, {@link #deleteAll},
   * {@link #delete}) и пересобирается при первом следующем поиске.
   */
  private final Map<URI, SymbolOccurrence[]> sortedOccurrences = new ConcurrentHashMap<>();

  /**
   * Получить все обращения к символам в указанном URI.
   *
   * @param uri URI документа, в котором необходимо найти обращения к символам.
   * @return Список найденных обращений к символам.
   */
  public Stream<SymbolOccurrence> getSymbolOccurrencesByLocationUri(URI uri) {
    return Arrays.stream(locations.getOrDefault(uri, EMPTY_OCCURRENCES));
  }

  /**
   * Найти вхождение к символу, чей диапазон накрывает позицию. Бинарный поиск
   * по отсортированному снимку вхождений документа: диапазоны вхождений
   * непересекающиеся, поэтому единственный кандидат — вхождение с наибольшим
   * началом диапазона, не превосходящим позицию.
   *
   * @param uri      URI документа.
   * @param position позиция.
   * @return вхождение в позиции либо empty.
   */
  public Optional<SymbolOccurrence> findByPosition(URI uri, Position position) {
    var sorted = sortedOccurrences.computeIfAbsent(uri, this::sortedSnapshot);
    var index = lastStartingAtOrBefore(sorted, position);
    if (index >= 0 && matches(sorted[index], position)) {
      return Optional.of(sorted[index]);
    }
    return Optional.empty();
  }

  private SymbolOccurrence[] sortedSnapshot(URI uri) {
    var snapshot = locations.getOrDefault(uri, EMPTY_OCCURRENCES).clone();
    Arrays.sort(snapshot, BY_RANGE_START);
    return snapshot;
  }

  /**
   * Индекс последнего вхождения, чей диапазон начинается не позже позиции,
   * либо {@code -1}, если все вхождения начинаются позже.
   */
  private static int lastStartingAtOrBefore(SymbolOccurrence[] sorted, Position position) {
    var low = 0;
    var high = sorted.length - 1;
    var found = -1;
    while (low <= high) {
      var mid = (low + high) >>> 1;
      if (startsAfter(sorted[mid].location(), position)) {
        high = mid - 1;
      } else {
        found = mid;
        low = mid + 1;
      }
    }
    return found;
  }

  private static boolean startsAfter(Location location, Position position) {
    return location.startLine() > position.getLine()
      || (location.startLine() == position.getLine() && location.startCharacter() > position.getCharacter());
  }

  private static boolean matches(SymbolOccurrence symbolOccurrence, Position position) {
    var location = symbolOccurrence.location();
    return Ranges.containsPosition(
      location.startLine(), location.startCharacter(), location.endLine(), location.endCharacter(),
      position);
  }

  /**
   * Заменить полный набор вхождений документа. Основной путь записи: перестроение
   * документа собирает набор в буфер и заменяет его целиком одним атомарным swap-ом.
   *
   * @param uri         URI документа.
   * @param occurrences Новый набор вхождений документа (без дубликатов).
   */
  public void replaceOccurrences(URI uri, Collection<SymbolOccurrence> occurrences) {
    if (occurrences.isEmpty()) {
      locations.remove(uri);
    } else {
      locations.put(uri, occurrences.toArray(EMPTY_OCCURRENCES));
    }
    sortedOccurrences.remove(uri);
  }

  /**
   * Добавить одиночное вхождение к символу (гранулярный путь записи). Дубликат по
   * {@code equals} игнорируется — семантика множества сохраняется.
   *
   * @param symbolOccurrence Обращение к символу.
   */
  public void updateLocation(SymbolOccurrence symbolOccurrence) {
    var uri = symbolOccurrence.location().uri();
    locations.merge(uri, new SymbolOccurrence[]{symbolOccurrence}, (existing, added) -> {
      if (contains(existing, added[0])) {
        return existing;
      }
      var updated = Arrays.copyOf(existing, existing.length + 1);
      updated[existing.length] = added[0];
      return updated;
    });
    sortedOccurrences.remove(uri);
  }

  /**
   * Удалить сохраненные расположения обращений к символам в указанном URI.
   *
   * @param uri URI документа для удаления расположений.
   */
  public void delete(URI uri) {
    locations.remove(uri);
    sortedOccurrences.remove(uri);
  }

  /**
   * Удалить перечисленные обращения к символам, расположенные в указанном URI.
   * Остальные обращения документа не затрагиваются.
   *
   * @param uri         URI документа.
   * @param occurrences Удаляемые обращения.
   */
  public void deleteAll(URI uri, Collection<SymbolOccurrence> occurrences) {
    locations.computeIfPresent(uri, (u, existing) -> {
      var remaining = Arrays.stream(existing)
        .filter(occurrence -> !occurrences.contains(occurrence))
        .toArray(SymbolOccurrence[]::new);
      return remaining.length == 0 ? null : remaining;
    });
    sortedOccurrences.remove(uri);
  }

  private static boolean contains(SymbolOccurrence[] array, SymbolOccurrence occurrence) {
    for (var element : array) {
      if (element.equals(occurrence)) {
        return true;
      }
    }
    return false;
  }
}
