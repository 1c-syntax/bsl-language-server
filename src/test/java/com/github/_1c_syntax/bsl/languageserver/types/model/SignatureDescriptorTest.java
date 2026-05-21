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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureDescriptorTest {

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Test
  void emptyConstant() {
    // given
    var empty = SignatureDescriptor.EMPTY;

    // when / then
    assertThat(empty.parameters()).isEmpty();
    assertThat(empty.returnTypes()).isSameAs(TypeSet.EMPTY);
    assertThat(empty.bilingualDescription()).isSameAs(BilingualString.EMPTY);
  }

  @Test
  void canonicalConstructorNormalizesNulls() {
    // given / when
    var sig = new SignatureDescriptor(List.of(), null, (BilingualString) null);

    // then
    assertThat(sig.returnTypes()).isSameAs(TypeSet.EMPTY);
    assertThat(sig.bilingualDescription()).isSameAs(BilingualString.EMPTY);
  }

  @Test
  void compatConstructorWithStringDescriptionWrapsToBilingual() {
    // given / when
    var sig = new SignatureDescriptor(List.of(), TypeSet.of(NUMBER), "описание");

    // then
    assertThat(sig.description()).isEqualTo("описание");
    assertThat(sig.bilingualDescription().ru()).isEqualTo("описание");
    assertThat(sig.bilingualDescription().en()).isEmpty();
  }

  @Test
  void compatConstructorWithSingleReturnTypeWrapsToTypeSet() {
    // given / when
    var sig = new SignatureDescriptor(List.of(), NUMBER, "");

    // then
    assertThat(sig.returnTypes().refs()).containsExactly(NUMBER);
    assertThat(sig.returnType()).isEqualTo(NUMBER);
  }

  @Test
  void compatConstructorWithUnknownReturnTypeGivesEmptyReturnTypes() {
    // given / when
    var sig = new SignatureDescriptor(List.of(), TypeRef.UNKNOWN, "");

    // then
    assertThat(sig.returnTypes()).isSameAs(TypeSet.EMPTY);
    assertThat(sig.returnType()).isSameAs(TypeRef.UNKNOWN);
  }

  @Test
  void compatConstructorWithNullReturnTypeGivesEmptyReturnTypes() {
    // given / when
    var sig = new SignatureDescriptor(List.of(), (TypeRef) null, "");

    // then
    assertThat(sig.returnTypes()).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void returnTypeAccessorPicksFirstFromUnion() {
    // given
    var union = TypeSet.of(NUMBER, STRING);
    var sig = new SignatureDescriptor(List.of(), union, "");

    // when
    var first = sig.returnType();

    // then
    assertThat(first).isEqualTo(NUMBER);
  }

  @Test
  void returnTypeUnknownWhenReturnTypesEmpty() {
    // given
    var sig = SignatureDescriptor.EMPTY;

    // when / then
    assertThat(sig.returnType()).isSameAs(TypeRef.UNKNOWN);
  }

  @Test
  void displayDescriptionRespectsLanguage() {
    // given
    var sig = new SignatureDescriptor(List.of(), TypeSet.EMPTY,
      BilingualString.of("ru-desc", "en-desc"));

    // when / then
    assertThat(sig.displayDescription(Language.RU)).isEqualTo("ru-desc");
    assertThat(sig.displayDescription(Language.EN)).isEqualTo("en-desc");
  }

  @Test
  void ofFactoryCreatesEmptyReturnsAndDescription() {
    // given
    var param = ParameterDescriptor.of("X");

    // when
    var sig = SignatureDescriptor.of(List.of(param));

    // then
    assertThat(sig.parameters()).containsExactly(param);
    assertThat(sig.returnTypes()).isSameAs(TypeSet.EMPTY);
    assertThat(sig.bilingualDescription()).isSameAs(BilingualString.EMPTY);
  }

  @Test
  void parametersAreUnmodifiableCopy() {
    // given
    var mutable = new java.util.ArrayList<ParameterDescriptor>();
    mutable.add(ParameterDescriptor.of("A"));
    var sig = new SignatureDescriptor(mutable, TypeSet.EMPTY, "");

    // when — мутируем исходный список после создания
    mutable.add(ParameterDescriptor.of("B"));

    // then — на дескрипторе остался снимок
    assertThat(sig.parameters()).hasSize(1);
  }
}
