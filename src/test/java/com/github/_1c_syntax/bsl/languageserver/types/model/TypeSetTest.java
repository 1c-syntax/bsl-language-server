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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeSetTest {

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");
  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");

  @Test
  void emptyConstants() {
    // given / when / then
    assertThat(TypeSet.EMPTY.isEmpty()).isTrue();
    assertThat(TypeSet.EMPTY.size()).isZero();
    assertThat(TypeSet.of()).isSameAs(TypeSet.EMPTY);
    assertThat(TypeSet.of(List.of())).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void ofVarargsCollectsRefsPreservingOrder() {
    // given / when
    var ts = TypeSet.of(NUMBER, STRING);

    // then
    assertThat(ts.refs()).containsExactly(NUMBER, STRING);
    assertThat(ts.size()).isEqualTo(2);
  }

  @Test
  void ofCollectionCollectsRefs() {
    // given / when
    var ts = TypeSet.of(List.of(ARRAY, STRUCTURE));

    // then
    assertThat(ts.refs()).containsExactly(ARRAY, STRUCTURE);
  }

  @Test
  void addAppendsRef() {
    // given
    var ts = TypeSet.of(NUMBER);

    // when
    var grown = ts.add(STRING);

    // then
    assertThat(grown.refs()).containsExactly(NUMBER, STRING);
  }

  @Test
  void unionMergesRefs() {
    // given
    var a = TypeSet.of(NUMBER);
    var b = TypeSet.of(STRING);

    // when
    var result = a.union(b);

    // then
    assertThat(result.refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void unionWithEmptyReturnsSelf() {
    // given
    var ts = TypeSet.of(NUMBER);

    // when / then
    assertThat(ts.union(TypeSet.EMPTY)).isSameAs(ts);
    assertThat(TypeSet.EMPTY.union(ts)).isSameAs(ts);
  }

  @Test
  void withElementAttachesElementTypes() {
    // given / when
    var array = TypeSet.of(ARRAY).withElement(ARRAY, TypeSet.of(NUMBER));

    // then
    assertThat(array.refs()).containsExactly(ARRAY);
    assertThat(array.getElementTypes(ARRAY).refs()).containsExactly(NUMBER);
    assertThat(array.getElementTypes().refs()).containsExactly(NUMBER);
  }

  @Test
  void withElementAddsRefIfMissing() {
    // given / when
    var ts = TypeSet.EMPTY.withElement(ARRAY, TypeSet.of(NUMBER));

    // then
    assertThat(ts.refs()).containsExactly(ARRAY);
  }

  @Test
  void withElementIgnoresEmpty() {
    // given
    var ts = TypeSet.of(ARRAY);

    // when
    var same = ts.withElement(ARRAY, TypeSet.EMPTY);

    // then
    assertThat(same).isSameAs(ts);
  }

  @Test
  void withFieldAttachesLocalField() {
    // given / when
    var ts = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Имя", TypeSet.of(STRING));

    // then
    assertThat(ts.getLocalFields(STRUCTURE)).containsKey("Имя");
    assertThat(ts.getFieldTypes("имя").refs()).containsExactly(STRING);
    assertThat(ts.getAllFieldNames()).containsExactly("Имя");
  }

  @Test
  void withFieldWithoutDescriptionHasEmptyDescription() {
    // given / when
    var ts = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Имя", TypeSet.of(STRING));

    // then
    assertThat(ts.getLocalFields(STRUCTURE).get("Имя").description()).isEmpty();
    assertThat(ts.getLocalFields(STRUCTURE).get("Имя").types().refs()).containsExactly(STRING);
  }

  @Test
  void withFieldCarriesDescription() {
    // given / when
    var ts = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Имя", TypeSet.of(STRING), "имя пользователя");

    // then
    assertThat(ts.getLocalFields(STRUCTURE).get("Имя").description()).isEqualTo("имя пользователя");
  }

  @Test
  void unionMergesFieldDescriptionsPreferringFirstNonBlank() {
    // given: одно и то же поле с описанием и без.
    var described = TypeSet.of(STRUCTURE).withField(STRUCTURE, "K", TypeSet.of(NUMBER), "описание K");
    var undescribed = TypeSet.of(STRUCTURE).withField(STRUCTURE, "K", TypeSet.of(STRING));

    // when: непустое описание выигрывает независимо от порядка.
    var leftFirst = described.union(undescribed);
    var rightFirst = undescribed.union(described);

    // then: типы объединены, описание — непустое.
    assertThat(leftFirst.getLocalFields(STRUCTURE).get("K").description()).isEqualTo("описание K");
    assertThat(rightFirst.getLocalFields(STRUCTURE).get("K").description()).isEqualTo("описание K");
    assertThat(leftFirst.getFieldTypes("K").refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void withFieldAddsRefIfMissing() {
    // given / when
    var ts = TypeSet.EMPTY.withField(STRUCTURE, "X", TypeSet.of(NUMBER));

    // then
    assertThat(ts.refs()).containsExactly(STRUCTURE);
  }

  @Test
  void getElementTypesEmptyByDefault() {
    // given
    var ts = TypeSet.of(ARRAY);

    // when / then
    assertThat(ts.getElementTypes(ARRAY)).isSameAs(TypeSet.EMPTY);
    assertThat(ts.getElementTypes(NUMBER)).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void getFieldTypesEmptyForUnknownField() {
    // given
    var ts = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Имя", TypeSet.of(STRING));

    // when / then
    assertThat(ts.getFieldTypes("НетТакого")).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void unionMergesElementTypesAndFields() {
    // given
    var arrayOfNumbers = TypeSet.of(ARRAY).withElement(ARRAY, TypeSet.of(NUMBER));
    var arrayOfStrings = TypeSet.of(ARRAY).withElement(ARRAY, TypeSet.of(STRING));
    var structWithNumber = TypeSet.of(STRUCTURE).withField(STRUCTURE, "K", TypeSet.of(NUMBER));
    var structWithString = TypeSet.of(STRUCTURE).withField(STRUCTURE, "K", TypeSet.of(STRING));

    // when
    var unionArrays = arrayOfNumbers.union(arrayOfStrings);
    var unionStructs = structWithNumber.union(structWithString);

    // then
    assertThat(unionArrays.getElementTypes(ARRAY).refs())
      .containsExactlyInAnyOrder(NUMBER, STRING);
    assertThat(unionStructs.getFieldTypes("K").refs())
      .containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void refsAreUnmodifiable() {
    // given
    Set<TypeRef> input = new HashSet<>();
    input.add(NUMBER);
    var ts = new TypeSet(input);

    // when / then
    assertThrows(UnsupportedOperationException.class, () -> ts.refs().add(STRING));
  }

  @Test
  void withLazyElementForcedOnRead() {
    // given: ленивый элемент массива — разрешается на чтении.
    var array = TypeSet.of(ARRAY)
      .withLazyElement(ARRAY, new LazyTypeSet("k", () -> TypeSet.of(STRUCTURE)));

    // then
    assertThat(array.refs()).containsExactly(ARRAY);
    assertThat(array.getElementTypes(ARRAY).refs()).containsExactly(STRUCTURE);
    assertThat(array.getElementTypes().refs()).containsExactly(STRUCTURE);
  }

  @Test
  void withLazyElementAddsRefIfMissing() {
    // given / when
    var ts = TypeSet.EMPTY.withLazyElement(ARRAY, new LazyTypeSet("k", () -> TypeSet.of(NUMBER)));

    // then
    assertThat(ts.refs()).containsExactly(ARRAY);
  }

  @Test
  void getElementTypesUnionsEagerAndLazy() {
    // given: у одного ref'а и eager-, и lazy-элемент.
    var ts = TypeSet.of(ARRAY)
      .withElement(ARRAY, TypeSet.of(NUMBER))
      .withLazyElement(ARRAY, new LazyTypeSet("k", () -> TypeSet.of(STRING)));

    // then
    assertThat(ts.getElementTypes(ARRAY).refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void withLazyFieldMaterializesWithTypeAndDescription() {
    // given / when
    var ts = TypeSet.of(STRUCTURE)
      .withLazyField(STRUCTURE, "Узел", new LazyTypeSet("k", () -> TypeSet.of(STRUCTURE)), "дочерний");

    // then
    var field = ts.getLocalFields(STRUCTURE).get("Узел");
    assertThat(field.types().refs()).containsExactly(STRUCTURE);
    assertThat(field.description()).isEqualTo("дочерний");
    assertThat(ts.getFieldTypes("узел").refs()).containsExactly(STRUCTURE);
  }

  @Test
  void getAllFieldNamesIncludesLazyWithoutForcing() {
    // given: резолвер ленивого поля бросил бы, если бы его форсили.
    var ts = TypeSet.of(STRUCTURE)
      .withField(STRUCTURE, "Прямое", TypeSet.of(STRING))
      .withLazyField(STRUCTURE, "Ленивое",
        new LazyTypeSet("k", () -> {
          throw new AssertionError("must not force for names");
        }), "");

    // then: имена известны без форса.
    assertThat(ts.getAllFieldNames()).containsExactlyInAnyOrder("Прямое", "Ленивое");
  }

  @Test
  void unionMergesLazyElementsAndFields() {
    // given
    var a = TypeSet.of(ARRAY)
      .withLazyElement(ARRAY, new LazyTypeSet("ea", () -> TypeSet.of(NUMBER)))
      .withLazyField(STRUCTURE, "K", new LazyTypeSet("fa", () -> TypeSet.of(NUMBER)), "");
    var b = TypeSet.of(ARRAY)
      .withLazyElement(ARRAY, new LazyTypeSet("eb", () -> TypeSet.of(STRING)))
      .withLazyField(STRUCTURE, "K", new LazyTypeSet("fb", () -> TypeSet.of(STRING)), "");

    // when
    var union = a.union(b);

    // then: разные ключи — оба форсятся и объединяются.
    assertThat(union.getElementTypes(ARRAY).refs()).containsExactlyInAnyOrder(NUMBER, STRING);
    assertThat(union.getFieldTypes("K").refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void addPreservesLazyDecorations() {
    // given
    var ts = TypeSet.of(ARRAY)
      .withLazyElement(ARRAY, new LazyTypeSet("k", () -> TypeSet.of(NUMBER)));

    // when
    var grown = ts.add(STRING);

    // then: ленивый элемент сохранён.
    assertThat(grown.refs()).containsExactlyInAnyOrder(ARRAY, STRING);
    assertThat(grown.getElementTypes(ARRAY).refs()).containsExactly(NUMBER);
  }

  @Test
  void withLazyFieldAccumulatesMultipleFields() {
    // given: два ленивых поля подряд — покрывает копирование существующих бакетов.
    var ts = TypeSet.of(STRUCTURE)
      .withLazyField(STRUCTURE, "A", new LazyTypeSet("a", () -> TypeSet.of(NUMBER)), "")
      .withLazyField(STRUCTURE, "B", new LazyTypeSet("b", () -> TypeSet.of(STRING)), "");

    // then
    assertThat(ts.getAllFieldNames()).containsExactlyInAnyOrder("A", "B");
    assertThat(ts.getFieldTypes("A").refs()).containsExactly(NUMBER);
    assertThat(ts.getFieldTypes("B").refs()).containsExactly(STRING);
  }

  @Test
  void unionAddsLazyDecorationsOnNewRefs() {
    // given: other несёт ленивые декорации на ref'ах, которых нет у this.
    var a = TypeSet.of(NUMBER);
    var b = TypeSet.of(ARRAY)
      .withLazyElement(ARRAY, new LazyTypeSet("e", () -> TypeSet.of(STRING)))
      .withLazyField(STRUCTURE, "K", new LazyTypeSet("f", () -> TypeSet.of(NUMBER)), "опис");

    // when
    var union = a.union(b);

    // then: новые бакеты ленивых декораций созданы.
    assertThat(union.getElementTypes(ARRAY).refs()).containsExactly(STRING);
    assertThat(union.getFieldTypes("K").refs()).containsExactly(NUMBER);
    assertThat(union.getLocalFields(STRUCTURE).get("K").description()).isEqualTo("опис");
  }

  @Test
  void getFieldTypesIgnoresNonMatchingLazyField() {
    // given: ленивое поле с другим именем — не должно попасть в выборку.
    var ts = TypeSet.of(STRUCTURE)
      .withLazyField(STRUCTURE, "Другое", new LazyTypeSet("k", () -> TypeSet.of(NUMBER)), "");

    // when / then
    assertThat(ts.getFieldTypes("Искомое")).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void unionWithLazyOnlyOtherIsNotShortCircuited() {
    // given: other непустой только по ленивым декорациям — не должен «потеряться».
    var self = TypeSet.of(NUMBER);
    var lazyOnly = TypeSet.of(ARRAY)
      .withLazyElement(ARRAY, new LazyTypeSet("k", () -> TypeSet.of(STRING)));

    // when
    var union = self.union(lazyOnly);

    // then
    assertThat(union.refs()).containsExactlyInAnyOrder(NUMBER, ARRAY);
    assertThat(union.getElementTypes(ARRAY).refs()).containsExactly(STRING);
  }
}
