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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeRefSpecializeTest {

  private static final TypeRef CATALOG_REF_GENERIC = new TypeRef(TypeKind.PLATFORM, "СправочникСсылка.<Имя справочника>");
  private static final TypeRef CATALOG_OBJECT_GENERIC = new TypeRef(TypeKind.PLATFORM, "СправочникОбъект.<Имя справочника>");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");

  @Test
  void placeholdersForGenericRef() {
    var placeholders = CATALOG_REF_GENERIC.placeholders();
    assertThat(placeholders).hasSize(1);
    var p = placeholders.get(0);
    assertThat(p.name()).isEqualTo("Имя справочника");
    assertThat(p.start()).isEqualTo("СправочникСсылка.".length());
    assertThat(p.end()).isEqualTo("СправочникСсылка.<Имя справочника>".length());
  }

  @Test
  void placeholdersForNonGenericRefIsEmpty() {
    assertThat(NUMBER.placeholders()).isEmpty();
  }

  @Test
  void placeholdersCachedAcrossCalls() {
    // Один и тот же TypeRef отдаёт тот же list при повторном вызове (через
    // PLACEHOLDER_CACHE по qualifiedName).
    var first = CATALOG_REF_GENERIC.placeholders();
    var second = CATALOG_REF_GENERIC.placeholders();
    assertThat(first).isSameAs(second);
  }

  @Test
  void specializeReplacesPlaceholderInQualifiedName() {
    var result = TypeRef.specialize(CATALOG_REF_GENERIC, Map.of("Имя справочника", "Контрагенты"));
    assertThat(result.qualifiedName()).isEqualTo("СправочникСсылка.Контрагенты");
    assertThat(result.kind()).isEqualTo(TypeKind.PLATFORM);
  }

  @Test
  void specializeMatchesPlaceholderCaseInsensitive() {
    var result = TypeRef.specialize(CATALOG_REF_GENERIC, Map.of("имя справочника", "X"));
    assertThat(result.qualifiedName()).isEqualTo("СправочникСсылка.X");
  }

  @Test
  void specializeWithoutMatchingBindingReturnsSameRef() {
    var result = TypeRef.specialize(CATALOG_REF_GENERIC, Map.of("Другое имя", "X"));
    assertThat(result).isSameAs(CATALOG_REF_GENERIC);
  }

  @Test
  void specializeOnNonGenericReturnsSameRef() {
    var result = TypeRef.specialize(NUMBER, Map.of("Имя", "X"));
    assertThat(result).isSameAs(NUMBER);
  }

  @Test
  void specializeUnknownIsNoOp() {
    var result = TypeRef.specialize(TypeRef.UNKNOWN, Map.of("a", "b"));
    assertThat(result).isSameAs(TypeRef.UNKNOWN);
  }

  @Test
  void specializeEmptyBindingsIsNoOp() {
    var result = TypeRef.specialize(CATALOG_REF_GENERIC, Map.of());
    assertThat(result).isSameAs(CATALOG_REF_GENERIC);
  }

  @Test
  void specializeTypeSetReplacesAllRefs() {
    var typeSet = TypeSet.of(List.of(CATALOG_REF_GENERIC, CATALOG_OBJECT_GENERIC));
    var result = TypeRef.specialize(typeSet, Map.of("Имя справочника", "Y"));
    var qualifiedNames = result.refs().stream().map(TypeRef::qualifiedName).toList();
    assertThat(qualifiedNames)
      .containsExactlyInAnyOrder("СправочникСсылка.Y", "СправочникОбъект.Y");
  }

  @Test
  void specializeTypeSetWithoutMatchReturnsSameSet() {
    var typeSet = TypeSet.of(NUMBER);
    var result = TypeRef.specialize(typeSet, Map.of("Имя справочника", "X"));
    assertThat(result).isSameAs(typeSet);
  }
}
