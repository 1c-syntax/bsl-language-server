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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmallCollectionsTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3, SmallCollections.MAX_ARRAY_SIZE, SmallCollections.MAX_ARRAY_SIZE + 1})
  void orderedMapKeepsContentOrderAndEquality(int size) {
    // given: ключи в порядке, отличном от естественного, — порядок вставки должен сохраниться
    var source = new LinkedHashMap<String, Integer>();
    for (var i = size; i > 0; i--) {
      source.put("ключ" + i, i);
    }

    // when
    var copy = SmallCollections.orderedMap(source);

    // then
    assertThat(copy).isEqualTo(source).hasSameHashCodeAs(source);
    assertThat(new ArrayList<>(copy.keySet())).containsExactlyElementsOf(source.keySet());
    var visited = new ArrayList<String>();
    copy.forEach((key, value) -> visited.add(key));
    assertThat(visited).containsExactlyElementsOf(source.keySet());
    source.forEach((key, value) -> assertThat(copy.get(key)).isEqualTo(value));
    assertThat(copy.get("нет такого")).isNull();
    assertThat(copy.containsKey("нет такого")).isFalse();
    assertThatThrownBy(() -> copy.put("новый", 0)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void orderedMapEntryIteratorIsReadOnlyAndBounded() {
    // given
    var copy = SmallCollections.orderedMap(Map.of("а", 1, "б", 2));
    var iterator = copy.entrySet().iterator();

    // when
    iterator.next();
    iterator.next();

    // then
    assertThat(iterator.hasNext()).isFalse();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(iterator::remove).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void orderedMapWithNullValueFallsBackToLinkedHashMapCopy() {
    // given
    var source = new HashMap<String, Integer>();
    source.put("а", null);
    source.put("б", 2);

    // when
    var copy = SmallCollections.orderedMap(source);

    // then
    assertThat(copy).isEqualTo(source).containsEntry("а", null);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3, SmallCollections.MAX_ARRAY_SIZE, SmallCollections.MAX_ARRAY_SIZE + 1})
  void orderedSetKeepsContentOrderAndEquality(int size) {
    // given
    var source = new LinkedHashSet<String>();
    for (var i = size; i > 0; i--) {
      source.add("элемент" + i);
    }

    // when
    var copy = SmallCollections.orderedSet(source);

    // then
    assertThat(copy).isEqualTo(source).hasSameHashCodeAs(source);
    assertThat(new ArrayList<>(copy)).containsExactlyElementsOf(source);
    var visited = new ArrayList<String>();
    copy.forEach(visited::add);
    assertThat(visited).containsExactlyElementsOf(source);
    source.forEach(element -> assertThat(copy).contains(element));
    assertThat(copy.contains("нет такого")).isFalse();
    assertThatThrownBy(() -> copy.add("новый")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void orderedSetIteratorIsBounded() {
    // given
    var iterator = SmallCollections.orderedSet(new LinkedHashSet<>(List.of("а", "б"))).iterator();

    // when
    iterator.next();
    iterator.next();

    // then
    assertThat(iterator.hasNext()).isFalse();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void orderedSetWithNullFallsBackToLinkedHashSetCopy() {
    // given
    var source = new LinkedHashSet<String>();
    source.add(null);
    source.add("б");

    // when
    var copy = SmallCollections.orderedSet(source);

    // then
    assertThat(copy).containsExactly(null, "б");
  }
}
