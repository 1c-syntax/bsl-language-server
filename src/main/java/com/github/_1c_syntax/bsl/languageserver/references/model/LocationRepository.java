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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Хранилище расположений обращений к символам.
 */
@Component
@WorkspaceScope
public class LocationRepository {
  /**
   * Список обращений к символу, сгруппированный по URI.
   */
  private final Map<URI, Set<SymbolOccurrence>> locations = new ConcurrentHashMap<>();

  /**
   * Вторичный индекс вхождений по строке их расположения — для O(1)-поиска
   * вхождения в позиции ({@link #findByPosition}) вместо линейного скана всего
   * набора вхождений документа. На одной строке может быть несколько вхождений
   * (разные колонки), поэтому значение — множество, разводимое по колонкам через
   * {@link Ranges#containsPosition} в {@link #findByPosition}.
   */
  private final Map<URI, Map<Integer, Set<SymbolOccurrence>>> locationsByLine = new ConcurrentHashMap<>();

  /**
   * Получить все обращения к символам в указанном URI.
   *
   * @param uri URI документа, в котором необходимо найти обращения к символам.
   * @return Список найденных обращений к символам.
   */
  public Stream<SymbolOccurrence> getSymbolOccurrencesByLocationUri(URI uri) {
    return locations.getOrDefault(uri, Collections.emptySet()).stream();
  }

  /**
   * Найти вхождение к символу, чей диапазон накрывает позицию. O(1)-lookup по
   * строке через {@link #locationsByLine}; при нескольких вхождениях на строке
   * разводит по колонке через {@link Ranges#containsPosition}. Диапазоны
   * вхождений непересекающиеся, поэтому совпадение максимум одно.
   *
   * @param uri      URI документа.
   * @param position позиция.
   * @return вхождение в позиции либо empty.
   */
  public Optional<SymbolOccurrence> findByPosition(URI uri, Position position) {
    var byLine = locationsByLine.get(uri);
    if (byLine == null) {
      return Optional.empty();
    }
    var bucket = byLine.get(position.getLine());
    if (bucket == null) {
      return Optional.empty();
    }
    for (var symbolOccurrence : bucket) {
      if (matches(symbolOccurrence, position)) {
        return Optional.of(symbolOccurrence);
      }
    }
    return Optional.empty();
  }

  private static boolean matches(SymbolOccurrence symbolOccurrence, Position position) {
    var location = symbolOccurrence.location();
    return Ranges.containsPosition(
      location.startLine(), location.startCharacter(), location.endLine(), location.endCharacter(),
      position);
  }

  /**
   * Обновить данные о расположении обращения к символу.
   *
   * @param symbolOccurrence Обращение к символу.
   */
  public void updateLocation(SymbolOccurrence symbolOccurrence) {
    var location = symbolOccurrence.location();
    locations.computeIfAbsent(location.uri(), uri -> ConcurrentHashMap.newKeySet())
      .add(symbolOccurrence);
    var byLine = locationsByLine.computeIfAbsent(location.uri(), uri -> new ConcurrentHashMap<>());
    for (var line = location.startLine(); line <= location.endLine(); line++) {
      byLine.computeIfAbsent(line, l -> ConcurrentHashMap.newKeySet()).add(symbolOccurrence);
    }
  }

  /**
   * Удалить сохраненные расположения обращений к символам в указанном URI.
   *
   * @param uri URI документа для удаления расположений.
   */
  public void delete(URI uri) {
    locations.remove(uri);
    locationsByLine.remove(uri);
  }
}
