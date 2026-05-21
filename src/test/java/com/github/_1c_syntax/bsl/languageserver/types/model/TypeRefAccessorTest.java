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

/**
 * Тесты на простые аксессоры {@link TypeRef}: canonicalKey, simpleName,
 * placeholders. Специализация generic-плейсхолдеров — в TypeRefSpecializeTest.
 */
class TypeRefAccessorTest {

  @Test
  void canonicalKeyLowercasesName() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "СправочникСсылка");

    // when
    var key = ref.canonicalKey();

    // then
    assertThat(key).isEqualTo("справочникссылка");
  }

  @Test
  void simpleNameStripsQualifier() {
    // given
    var qualified = new TypeRef(TypeKind.CONFIGURATION, "Справочники.Контрагенты");
    var plain = new TypeRef(TypeKind.PRIMITIVE, "Число");

    // when / then
    assertThat(qualified.simpleName()).isEqualTo("Контрагенты");
    assertThat(plain.simpleName()).isEqualTo("Число");
  }

  @Test
  void unknownAndAnyConstants() {
    // given / when / then
    assertThat(TypeRef.UNKNOWN.kind()).isEqualTo(TypeKind.UNKNOWN);
    assertThat(TypeRef.UNKNOWN.qualifiedName()).isEqualTo("Unknown");
    assertThat(TypeRef.ANY.kind()).isEqualTo(TypeKind.ANY);
    assertThat(TypeRef.ANY.qualifiedName()).isEqualTo("Any");
  }

  @Test
  void placeholdersEmptyForPlainName() {
    // given
    var ref = new TypeRef(TypeKind.PRIMITIVE, "Число");

    // when
    var placeholders = ref.placeholders();

    // then
    assertThat(placeholders).isEmpty();
  }

  @Test
  void placeholdersDetectGenericSlot() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "СправочникСсылка.<Имя справочника>");

    // when
    var placeholders = ref.placeholders();

    // then
    assertThat(placeholders).isNotEmpty();
  }
}
