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
 * Покрывает {@link Type#displayName} default-метод + ref-аксессоры
 * sealed-имплементаций.
 */
class TypeImplsTest {

  @Test
  void anyTypeRefIsAny() {
    assertThat(AnyType.INSTANCE.ref()).isEqualTo(TypeRef.ANY);
    assertThat(AnyType.INSTANCE.displayName()).isEqualTo(TypeRef.ANY.qualifiedName());
  }

  @Test
  void unknownTypeRefIsUnknown() {
    assertThat(UnknownType.INSTANCE.ref()).isEqualTo(TypeRef.UNKNOWN);
    assertThat(UnknownType.INSTANCE.displayName()).isEqualTo(TypeRef.UNKNOWN.qualifiedName());
  }

  @Test
  void primitiveTypeRetainsItsRef() {
    var ref = new TypeRef(TypeKind.PRIMITIVE, "Число");
    var type = new PrimitiveType(ref);

    assertThat(type.ref()).isEqualTo(ref);
    assertThat(type.displayName()).isEqualTo("Число");
  }

  @Test
  void platformTypeRetainsItsRef() {
    var ref = new TypeRef(TypeKind.PLATFORM, "Массив");
    var type = new PlatformType(ref);

    assertThat(type.ref()).isEqualTo(ref);
  }

  @Test
  void configurationTypeRetainsItsRef() {
    var ref = new TypeRef(TypeKind.CONFIGURATION, "Справочник.Контрагенты");
    var type = new ConfigurationType(ref);

    assertThat(type.ref()).isEqualTo(ref);
  }
}
