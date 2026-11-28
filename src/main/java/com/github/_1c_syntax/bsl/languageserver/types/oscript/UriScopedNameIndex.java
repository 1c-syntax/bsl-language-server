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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Индекс «имя → значения» с точечным удалением вклада одного документа.
 * <p>
 * Имя сравнивается без учёта регистра. Каждое значение знает документ, из которого оно пришло
 * (функция {@code sourceOf}), — по нему {@link #removeByUri} убирает только вклад этого
 * документа, а опустевшие имена удаляет из индекса. Потокобезопасен.
 *
 * @param <V> тип значения.
 */
public final class UriScopedNameIndex<V> {

  private final Function<V, URI> sourceOf;
  /** Имя (lowercase) → значения. */
  private final Map<String, Set<V>> valuesByName = new ConcurrentHashMap<>();
  /** Документ → имена (lowercase), под которыми он что-то добавил. */
  private final Map<URI, Set<String>> namesByUri = new ConcurrentHashMap<>();

  /**
   * Создать пустой индекс.
   *
   * @param sourceOf документ, из которого пришло значение.
   */
  public UriScopedNameIndex(Function<V, URI> sourceOf) {
    this.sourceOf = sourceOf;
  }

  /**
   * Добавить значение под именем.
   *
   * @param name  имя (регистр не важен).
   * @param value значение; его документ — {@code sourceOf(value)}.
   */
  public void add(String name, V value) {
    var key = name.toLowerCase(Locale.ROOT);
    valuesByName.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(value);
    namesByUri.computeIfAbsent(sourceOf.apply(value), u -> ConcurrentHashMap.newKeySet()).add(key);
  }

  /**
   * Значения под именем.
   *
   * @param name имя (регистр не важен).
   * @return неизменяемый вид значений; пустое множество, если имени в индексе нет.
   */
  public Set<V> get(String name) {
    var values = valuesByName.get(name.toLowerCase(Locale.ROOT));
    return values == null ? Set.of() : Collections.unmodifiableSet(values);
  }

  /**
   * Имена, под которыми документ что-то добавил.
   *
   * @param uri документ.
   * @return неизменяемый вид имён в нижнем регистре; пустое множество, если вклада нет.
   */
  public Set<String> namesOf(URI uri) {
    var names = namesByUri.get(uri);
    return names == null ? Set.of() : Collections.unmodifiableSet(names);
  }

  /**
   * Убрать всё, что добавлено из документа.
   *
   * @param uri документ.
   */
  public void removeByUri(URI uri) {
    var names = namesByUri.remove(uri);
    if (names == null) {
      return;
    }
    for (var name : names) {
      valuesByName.computeIfPresent(name, (key, values) -> {
        values.removeIf(value -> uri.equals(sourceOf.apply(value)));
        return values.isEmpty() ? null : values;
      });
    }
  }

  /**
   * Очистить индекс.
   */
  public void clear() {
    valuesByName.clear();
    namesByUri.clear();
  }
}
