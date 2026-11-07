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

import static org.assertj.core.api.Assertions.assertThat;

class LazyTypeSetTest {

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Test
  void getReturnsResolvedValue() {
    var lazy = new LazyTypeSet("k", () -> TypeSet.of(NUMBER));

    assertThat(lazy.get().refs()).containsExactly(NUMBER);
  }

  @Test
  void getReturnsEmptyWhenResolverYieldsNull() {
    var lazy = new LazyTypeSet("k", () -> null);

    assertThat(lazy.get()).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void keysAreExposed() {
    var combined = LazyTypeSet.combine(
      new LazyTypeSet("ключ", () -> TypeSet.EMPTY),
      new LazyTypeSet("другой", () -> TypeSet.EMPTY));

    assertThat(combined.keys()).containsExactly("ключ", "другой");
  }

  @Test
  void mapAppliesToEachSourceKeepingKeys() {
    var combined = LazyTypeSet.combine(
      new LazyTypeSet("a", () -> TypeSet.of(NUMBER)),
      new LazyTypeSet("b", () -> null));

    var mapped = combined.map(types -> types.add(STRING));

    assertThat(mapped).isEqualTo(combined);
    assertThat(mapped.get().refs()).containsExactly(NUMBER, STRING);
  }

  @Test
  void notMemoizedReflectsResolverEachCall() {
    // немемоизирующий: get() обращается к актуальному источнику каждый раз.
    var counter = new int[]{0};
    var lazy = new LazyTypeSet("k", () -> {
      counter[0]++;
      return TypeSet.of(NUMBER);
    });

    lazy.get();
    lazy.get();

    assertThat(counter[0]).isEqualTo(2);
  }

  @Test
  void equalsAndHashCodeByKeyIgnoringResolver() {
    var a = new LazyTypeSet("k", () -> TypeSet.of(NUMBER));
    var b = new LazyTypeSet("k", () -> TypeSet.of(STRING));
    var c = new LazyTypeSet("other", () -> TypeSet.of(NUMBER));

    assertThat(a)
      .isEqualTo(b)
      .hasSameHashCodeAs(b)
      .isNotEqualTo(c)
      .isNotEqualTo("not a lazy");
  }

  @Test
  void combineSameKeyReturnsFirst() {
    var a = new LazyTypeSet("k", () -> TypeSet.of(NUMBER));
    var b = new LazyTypeSet("k", () -> TypeSet.of(STRING));

    assertThat(LazyTypeSet.combine(a, b)).isSameAs(a);
  }

  @Test
  void combineDifferentKeysUnionsBoth() {
    var a = new LazyTypeSet("a", () -> TypeSet.of(NUMBER));
    var b = new LazyTypeSet("b", () -> TypeSet.of(STRING));

    var combined = LazyTypeSet.combine(a, b);

    assertThat(combined.get().refs()).containsExactlyInAnyOrder(NUMBER, STRING);
    assertThat(combined).isNotEqualTo(a);
  }

  @Test
  void combineWithAlreadyIncludedSourceReturnsSame() {
    var a = new LazyTypeSet("a", () -> TypeSet.of(NUMBER));
    var b = new LazyTypeSet("b", () -> TypeSet.of(STRING));
    var combined = LazyTypeSet.combine(a, b);

    // источник, уже вошедший в объединение, его не меняет — с какой стороны ни добавляй.
    assertThat(LazyTypeSet.combine(combined, a)).isSameAs(combined);
    assertThat(LazyTypeSet.combine(b, combined)).isSameAs(combined);
  }

  @Test
  void combineIsEqualRegardlessOfOrderAndNesting() {
    var a = new LazyTypeSet("a", () -> TypeSet.of(NUMBER));
    var b = new LazyTypeSet("b", () -> TypeSet.of(STRING));
    var c = new LazyTypeSet("c", () -> TypeSet.EMPTY);

    var left = LazyTypeSet.combine(LazyTypeSet.combine(a, b), c);
    var right = LazyTypeSet.combine(c, LazyTypeSet.combine(b, a));

    assertThat(left)
      .isEqualTo(right)
      .hasSameHashCodeAs(right);
  }

  @Test
  void repeatedlyCombinedSourceIsResolvedOncePerGet() {
    // given: так копится значение на кругах расчёта по потоку — на каждом слиянии веток
    // обе стороны несут прежнее значение, дополненное одним и тем же источником.
    var counter = new int[]{0};
    var a = new LazyTypeSet("a", () -> {
      counter[0]++;
      return TypeSet.of(NUMBER);
    });
    var b = new LazyTypeSet("b", () -> TypeSet.of(STRING));
    var accumulated = a;
    for (var i = 0; i < 10; i++) {
      accumulated = LazyTypeSet.combine(
        LazyTypeSet.combine(accumulated, a),
        LazyTypeSet.combine(accumulated, b));
    }

    // when
    var resolved = accumulated.get();

    // then
    assertThat(resolved.refs()).containsExactlyInAnyOrder(NUMBER, STRING);
    assertThat(counter[0]).isEqualTo(1);
  }

  @Test
  void toStringContainsKey() {
    assertThat(new LazyTypeSet("МойКлюч", () -> TypeSet.EMPTY)).hasToString("Lazy(МойКлюч)");
  }
}
