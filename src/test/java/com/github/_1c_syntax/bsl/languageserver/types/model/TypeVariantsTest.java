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

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты разновидностей {@link Type} (sealed-иерархия).
 */
@ExtendWith(MockitoExtension.class)
class TypeVariantsTest {

  @Mock
  private SourceDefinedSymbol symbol;

  @Test
  void anyTypeSingletonRefIsAny() {
    // given
    var t = AnyType.INSTANCE;

    // when / then
    assertThat(t.ref()).isSameAs(TypeRef.ANY);
    assertThat(t).isEqualTo(new AnyType());
  }

  @Test
  void unknownTypeSingletonRefIsUnknown() {
    // given
    var t = UnknownType.INSTANCE;

    // when / then
    assertThat(t.ref()).isSameAs(TypeRef.UNKNOWN);
    assertThat(t).isEqualTo(new UnknownType());
  }

  @Test
  void primitiveTypeCarriesRef() {
    // given
    var ref = new TypeRef(TypeKind.PRIMITIVE, "Число");

    // when
    var t = new PrimitiveType(ref);

    // then
    assertThat(t.ref()).isEqualTo(ref);
  }

  @Test
  void platformTypeCarriesRef() {
    // given
    var ref = new TypeRef(TypeKind.PLATFORM, "Массив");

    // when
    var t = new PlatformType(ref);

    // then
    assertThat(t.ref()).isEqualTo(ref);
  }

  @Test
  void configurationTypeCarriesRef() {
    // given
    var ref = new TypeRef(TypeKind.CONFIGURATION, "Справочники.Контрагенты");

    // when
    var t = new ConfigurationType(ref);

    // then
    assertThat(t.ref()).isEqualTo(ref);
  }

  @Test
  void userTypeKeepsWeakReferenceToDeclaration() {
    // given
    var ref = new TypeRef(TypeKind.USER, "ОбщийМодуль1");

    // when
    var t = new UserType(ref, symbol);

    // then
    assertThat(t.ref()).isEqualTo(ref);
    assertThat(t.getDeclaration()).contains(symbol);
  }

  @Test
  void userTypeReturnsEmptyDeclarationWhenWeakRefCleared() {
    // given — создаём через варианты без сильной ссылки
    var ref = new TypeRef(TypeKind.USER, "X");
    var t = new UserType(ref, new java.lang.ref.WeakReference<>(null));

    // when
    var decl = t.getDeclaration();

    // then
    assertThat(decl).isEmpty();
  }
}
