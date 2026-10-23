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
package com.github._1c_syntax.bsl.languageserver.types.model;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Неизменяемые копии карт и множеств {@link TypeSet} с сохранением порядка вставки, компактные
 * для малых размеров.
 * <p>
 * Наборов типов в памяти миллионы, и почти все их коллекции — из одного-нескольких элементов:
 * типы элементов одной коллекции, поля одной структуры, два-три типа объединения. Обёртка над
 * {@link LinkedHashMap} с её таблицей и узлами стоила такой коллекции в несколько раз больше,
 * чем массив тех же ссылок. Поиск в малых коллекциях — перебором, он не медленнее хэширования.
 */
final class SmallCollections {

  /** Наибольший размер, который хранится массивом; крупнее — {@link LinkedHashMap}/{@link LinkedHashSet}. */
  static final int MAX_ARRAY_SIZE = 8;

  private SmallCollections() {
    // фабрики статические
  }

  /**
   * Неизменяемая копия карты с порядком вставки.
   *
   * @param source исходная карта.
   * @return пустая, одноэлементная, на массиве (до {@link #MAX_ARRAY_SIZE}) либо копия {@link LinkedHashMap}.
   */
  static <K, V> Map<K, V> orderedMap(Map<K, V> source) {
    var size = source.size();
    if (size == 0) {
      return Collections.emptyMap();
    }
    if (size > MAX_ARRAY_SIZE || hasNulls(source)) {
      return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
    if (size == 1) {
      var entry = source.entrySet().iterator().next();
      return Map.of(entry.getKey(), entry.getValue());
    }
    var keysAndValues = new Object[size * 2];
    var i = 0;
    for (var entry : source.entrySet()) {
      keysAndValues[i++] = entry.getKey();
      keysAndValues[i++] = entry.getValue();
    }
    return new ArrayMap<>(keysAndValues);
  }

  /**
   * Неизменяемая копия множества с порядком вставки.
   *
   * @param source исходные элементы без повторов.
   * @return пустое, одноэлементное, на массиве (до {@link #MAX_ARRAY_SIZE}) либо копия {@link LinkedHashSet}.
   */
  static <E> Set<E> orderedSet(Collection<E> source) {
    var size = source.size();
    if (size == 0) {
      return Collections.emptySet();
    }
    if (size > MAX_ARRAY_SIZE) {
      return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
    var elements = source.toArray();
    if (hasNulls(elements)) {
      return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
    if (size == 1) {
      return Set.of(source.iterator().next());
    }
    return new ArraySet<>(elements);
  }

  private static boolean hasNulls(Object[] elements) {
    for (var element : elements) {
      if (element == null) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNulls(Map<?, ?> source) {
    for (var entry : source.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Карта на массиве: ключи и значения вперемежку, в порядке вставки.
   */
  private static final class ArrayMap<K, V> extends AbstractMap<K, V> {

    private final Object[] keysAndValues;

    private ArrayMap(Object[] keysAndValues) {
      this.keysAndValues = keysAndValues;
    }

    @Override
    public int size() {
      return keysAndValues.length / 2;
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
      return indexOf(key) >= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable V get(@Nullable Object key) {
      var index = indexOf(key);
      return index < 0 ? null : (V) keysAndValues[index + 1];
    }

    private int indexOf(@Nullable Object key) {
      for (var i = 0; i < keysAndValues.length; i += 2) {
        if (keysAndValues[i].equals(key)) {
          return i;
        }
      }
      return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<? super K, ? super V> action) {
      for (var i = 0; i < keysAndValues.length; i += 2) {
        action.accept((K) keysAndValues[i], (V) keysAndValues[i + 1]);
      }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return new AbstractSet<>() {
        @Override
        public Iterator<Entry<K, V>> iterator() {
          return new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
              return next < keysAndValues.length;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Entry<K, V> next() {
              if (!hasNext()) {
                throw new NoSuchElementException();
              }
              var entry = Map.entry((K) keysAndValues[next], (V) keysAndValues[next + 1]);
              next += 2;
              return entry;
            }
          };
        }

        @Override
        public int size() {
          return ArrayMap.this.size();
        }
      };
    }
  }

  /**
   * Множество на массиве, в порядке вставки.
   */
  private static final class ArraySet<E> extends AbstractSet<E> {

    private final Object[] elements;

    private ArraySet(Object[] elements) {
      this.elements = elements;
    }

    @Override
    public int size() {
      return elements.length;
    }

    @Override
    public boolean contains(@Nullable Object element) {
      for (var candidate : elements) {
        if (Objects.equals(candidate, element)) {
          return true;
        }
      }
      return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(Consumer<? super E> action) {
      for (var element : elements) {
        action.accept((E) element);
      }
    }

    @Override
    public Iterator<E> iterator() {
      return new Iterator<>() {
        private int next;

        @Override
        public boolean hasNext() {
          return next < elements.length;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          return (E) elements[next++];
        }
      };
    }
  }
}
