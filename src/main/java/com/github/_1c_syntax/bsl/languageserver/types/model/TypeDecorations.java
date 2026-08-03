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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Операции над декорационными картами {@link TypeSet}: копирование, слияние, замена
 * ключей и отбор.
 * <p>
 * Декораций у набора несколько (типы элементов, поля, их ленивые варианты, описываемые
 * типы), и все они устроены одинаково — плоская карта «тип → значение» либо вложенная
 * «тип → имя → значение». Плотничество по этим картам одно на все декорации и живёт
 * здесь, а не в самом наборе: набор остаётся значением, а не сборником утилит.
 */
final class TypeDecorations {

  private TypeDecorations() {
    // операции статические
  }

  /** Неизменяемая копия плоской карты (пустая — общий emptyMap). */
  static <K, V> Map<K, V> immutableCopy(Map<K, V> source) {
    return source == null || source.isEmpty()
      ? Collections.emptyMap()
      : Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  /** Неизменяемая глубокая копия вложенной карты (ключ → имя → значение). */
  static <K, N, V> Map<K, Map<N, V>> immutableNestedCopy(Map<K, Map<N, V>> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyMap();
    }
    var copy = new LinkedHashMap<K, Map<N, V>>();
    for (var entry : source.entrySet()) {
      copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
    }
    return Collections.unmodifiableMap(copy);
  }

  /** Слияние плоских карт: пересекающиеся ключи сливаются {@code merger}. */
  static <V> Map<TypeRef, V> mergedFlat(Map<TypeRef, V> first, Map<TypeRef, V> second,
                                        BinaryOperator<V> merger) {
    if (second.isEmpty()) {
      return first;
    }
    var result = new LinkedHashMap<>(first);
    second.forEach((TypeRef ref, V value) -> result.merge(ref, value, merger));
    return result;
  }

  /** Слияние вложенных карт: сливаются и типы-ключи, и записи внутри них. */
  static <V> Map<TypeRef, Map<String, V>> mergedNested(Map<TypeRef, Map<String, V>> first,
                                                       Map<TypeRef, Map<String, V>> second,
                                                       BinaryOperator<V> merger) {
    if (second.isEmpty()) {
      return first;
    }
    var result = new LinkedHashMap<TypeRef, Map<String, V>>();
    first.forEach((TypeRef ref, Map<String, V> entries) -> result.put(ref, new LinkedHashMap<>(entries)));
    second.forEach((TypeRef ref, Map<String, V> entries) -> {
      var target = result.computeIfAbsent(ref, key -> new LinkedHashMap<>());
      entries.forEach((String name, V value) -> target.merge(name, value, merger));
    });
    return result;
  }

  /** Замена ключей плоской карты: сошедшиеся в один ключ значения сливаются. */
  static <V> Map<TypeRef, V> mapKeys(Map<TypeRef, V> source, UnaryOperator<TypeRef> mapper,
                                     BinaryOperator<V> merger, UnaryOperator<V> valueMapper) {
    if (source.isEmpty()) {
      return source;
    }
    var result = new LinkedHashMap<TypeRef, V>();
    source.forEach((TypeRef ref, V value) -> result.merge(mapper.apply(ref), valueMapper.apply(value), merger));
    return result;
  }

  /** Замена ключей вложенной карты: значения по совпавшим именам сливаются. */
  static <V> Map<TypeRef, Map<String, V>> mapNestedKeys(Map<TypeRef, Map<String, V>> source,
                                                        UnaryOperator<TypeRef> mapper,
                                                        BinaryOperator<V> merger,
                                                        UnaryOperator<V> valueMapper) {
    if (source.isEmpty()) {
      return source;
    }
    var result = new LinkedHashMap<TypeRef, Map<String, V>>();
    source.forEach((TypeRef ref, Map<String, V> entries) -> {
      var target = result.computeIfAbsent(mapper.apply(ref), key -> new LinkedHashMap<>());
      entries.forEach((String name, V value) -> target.merge(name, valueMapper.apply(value), merger));
    });
    return result;
  }

  /** Карта без записей по отсеянным типам. */
  static <V> Map<TypeRef, V> filterByKey(Map<TypeRef, V> source, Predicate<TypeRef> keep) {
    if (source.isEmpty()) {
      return source;
    }
    var copy = new LinkedHashMap<TypeRef, V>();
    for (var entry : source.entrySet()) {
      if (keep.test(entry.getKey())) {
        copy.put(entry.getKey(), entry.getValue());
      }
    }
    return copy;
  }
}
