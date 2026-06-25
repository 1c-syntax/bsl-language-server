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

class LazyFieldTest {

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Test
  void materializeForcesTypeAndKeepsDescription() {
    var field = new LazyField(new LazyTypeSet("k", () -> TypeSet.of(NUMBER)), "описание");

    var materialized = field.materialize();

    assertThat(materialized.types().refs()).containsExactly(NUMBER);
    assertThat(materialized.description()).isEqualTo("описание");
  }

  @Test
  void nullDescriptionBecomesEmpty() {
    var field = new LazyField(new LazyTypeSet("k", () -> TypeSet.EMPTY), null);

    assertThat(field.description()).isEmpty();
  }

  @Test
  void mergeCombinesTypesAndPrefersFirstNonBlankDescription() {
    var described = new LazyField(new LazyTypeSet("a", () -> TypeSet.of(NUMBER)), "опис");
    var undescribed = new LazyField(new LazyTypeSet("b", () -> TypeSet.of(STRING)), "");

    var leftFirst = LazyField.merge(described, undescribed);
    var rightFirst = LazyField.merge(undescribed, described);

    assertThat(leftFirst.description()).isEqualTo("опис");
    assertThat(rightFirst.description()).isEqualTo("опис");
    assertThat(leftFirst.types().get().refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }
}
