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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSetTest {

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");
  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");

  @Test
  void emptyConstants() {
    assertThat(TypeSet.EMPTY.isEmpty()).isTrue();
    assertThat(TypeSet.EMPTY.size()).isZero();
    assertThat(TypeSet.of()).isSameAs(TypeSet.EMPTY);
    assertThat(TypeSet.of(List.of())).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void ofVarargsCollectsRefsPreservingOrder() {
    var ts = TypeSet.of(NUMBER, STRING);
    assertThat(ts.refs()).containsExactly(NUMBER, STRING);
    assertThat(ts.size()).isEqualTo(2);
  }

  @Test
  void ofCollectionCollectsRefs() {
    var ts = TypeSet.of(List.of(ARRAY, STRUCTURE));
    assertThat(ts.refs()).containsExactly(ARRAY, STRUCTURE);
  }

  @Test
  void addAppendsRef() {
    var ts = TypeSet.of(NUMBER).add(STRING);
    assertThat(ts.refs()).containsExactly(NUMBER, STRING);
  }

  @Test
  void unionMergesRefs() {
    var a = TypeSet.of(NUMBER);
    var b = TypeSet.of(STRING);
    var result = a.union(b);
    assertThat(result.refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void unionWithEmptyReturnsSelf() {
    var ts = TypeSet.of(NUMBER);
    assertThat(ts.union(TypeSet.EMPTY)).isSameAs(ts);
    assertThat(TypeSet.EMPTY.union(ts)).isSameAs(ts);
  }

  @Test
  void withElementAttachesElementTypes() {
    var array = TypeSet.of(ARRAY).withElement(ARRAY, TypeSet.of(NUMBER));
    assertThat(array.refs()).containsExactly(ARRAY);
    assertThat(array.getElementTypes(ARRAY).refs()).containsExactly(NUMBER);
    assertThat(array.getElementTypes().refs()).containsExactly(NUMBER);
  }

  @Test
  void withElementAddsRefIfMissing() {
    var ts = TypeSet.EMPTY.withElement(ARRAY, TypeSet.of(NUMBER));
    assertThat(ts.refs()).containsExactly(ARRAY);
  }

  @Test
  void withElementIgnoresEmpty() {
    var ts = TypeSet.of(ARRAY);
    assertThat(ts.withElement(ARRAY, TypeSet.EMPTY)).isSameAs(ts);
  }

  @Test
  void withFieldAttachesLocalField() {
    var ts = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Имя", TypeSet.of(STRING));
    assertThat(ts.getLocalFields(STRUCTURE)).containsKey("Имя");
    assertThat(ts.getFieldTypes("имя").refs()).containsExactly(STRING);
    assertThat(ts.getAllFieldNames()).containsExactly("Имя");
  }

  @Test
  void withFieldAddsRefIfMissing() {
    var ts = TypeSet.EMPTY.withField(STRUCTURE, "X", TypeSet.of(NUMBER));
    assertThat(ts.refs()).containsExactly(STRUCTURE);
  }

  @Test
  void getElementTypesEmptyByDefault() {
    assertThat(TypeSet.of(ARRAY).getElementTypes(ARRAY)).isSameAs(TypeSet.EMPTY);
    assertThat(TypeSet.of(ARRAY).getElementTypes(NUMBER)).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void getFieldTypesEmptyForUnknownField() {
    var ts = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Имя", TypeSet.of(STRING));
    assertThat(ts.getFieldTypes("НетТакого")).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void unionMergesElementTypesAndFields() {
    var a = TypeSet.of(ARRAY).withElement(ARRAY, TypeSet.of(NUMBER));
    var b = TypeSet.of(ARRAY).withElement(ARRAY, TypeSet.of(STRING));
    var u = a.union(b);
    assertThat(u.getElementTypes(ARRAY).refs()).containsExactlyInAnyOrder(NUMBER, STRING);

    var s1 = TypeSet.of(STRUCTURE).withField(STRUCTURE, "K", TypeSet.of(NUMBER));
    var s2 = TypeSet.of(STRUCTURE).withField(STRUCTURE, "K", TypeSet.of(STRING));
    var su = s1.union(s2);
    assertThat(su.getFieldTypes("K").refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void refsAreUnmodifiable() {
    Set<TypeRef> input = new java.util.HashSet<>();
    input.add(NUMBER);
    var ts = new TypeSet(input);
    org.junit.jupiter.api.Assertions.assertThrows(
      UnsupportedOperationException.class,
      () -> ts.refs().add(STRING)
    );
  }
}
