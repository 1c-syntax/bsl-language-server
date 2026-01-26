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

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Хранилище расположений обращений к символам.
 */
@Component
public class LocationRepository {
  /**
   * Список обращений к символу, сгруппированный по URI.
   */
  private final Map<URI, Set<SymbolOccurrence>> locations = new ConcurrentHashMap<>();

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
   * Обновить данные о расположении обращения к символу.
   *
   * @param symbolOccurrence Обращение к символу.
   */
  public void updateLocation(SymbolOccurrence symbolOccurrence) {
    locations.computeIfAbsent(symbolOccurrence.location().uri(), uri -> ConcurrentHashMap.newKeySet())
      .add(symbolOccurrence);
  }

  /**
   * Удалить сохраненные расположения обращений к символам в указанном URI.
   *
   * @param uri URI документа для удаления расположений.
   */
  public void delete(URI uri) {
    locations.remove(uri);
  }
}
