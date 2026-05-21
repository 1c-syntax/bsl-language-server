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

import static org.assertj.core.api.Assertions.assertThat;

class ParameterDescriptorTest {

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");

  @Test
  void canonicalConstructorNormalizesNulls() {
    // given / when
    var p = new ParameterDescriptor(
      (BilingualString) null, TypeSet.of(NUMBER), false, (BilingualString) null, null);

    // then
    assertThat(p.bilingualName()).isSameAs(BilingualString.EMPTY);
    assertThat(p.bilingualDescription()).isSameAs(BilingualString.EMPTY);
    assertThat(p.defaultValue()).isEmpty();
  }

  @Test
  void compatConstructorWithBilingualName() {
    // given
    var bilingual = BilingualString.of("Имя", "Name");

    // when
    var p = new ParameterDescriptor("Имя", TypeSet.of(NUMBER), false, "описание", "0", bilingual);

    // then
    assertThat(p.bilingualName()).isSameAs(bilingual);
    assertThat(p.description()).isEqualTo("описание");
    assertThat(p.defaultValue()).isEqualTo("0");
  }

  @Test
  void compatConstructorBilingualNameFallbackToName() {
    // given / when
    var p = new ParameterDescriptor("Имя", TypeSet.of(NUMBER), false, "описание",
      "0", BilingualString.EMPTY);

    // then
    assertThat(p.bilingualName().ru()).isEqualTo("Имя");
    assertThat(p.bilingualName().en()).isEmpty();
  }

  @Test
  void compatConstructorWithoutBilingualName() {
    // given / when
    var p = new ParameterDescriptor("Имя", TypeSet.of(NUMBER), true, "опц", "1");

    // then
    assertThat(p.bilingualName().ru()).isEqualTo("Имя");
    assertThat(p.bilingualDescription().ru()).isEqualTo("опц");
    assertThat(p.optional()).isTrue();
    assertThat(p.defaultValue()).isEqualTo("1");
  }

  @Test
  void compatConstructorWithoutDefaultValue() {
    // given / when
    var p = new ParameterDescriptor("Имя", TypeSet.of(NUMBER), false, "опис");

    // then
    assertThat(p.name()).isEqualTo("Имя");
    assertThat(p.defaultValue()).isEmpty();
  }

  @Test
  void ofFactoryRequiredParameter() {
    // given / when
    var p = ParameterDescriptor.of("X");

    // then
    assertThat(p.name()).isEqualTo("X");
    assertThat(p.optional()).isFalse();
    assertThat(p.types()).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void ofFactoryOptionalParameter() {
    // given / when
    var p = ParameterDescriptor.of("Y", true);

    // then
    assertThat(p.name()).isEqualTo("Y");
    assertThat(p.optional()).isTrue();
  }

  @Test
  void matchesIsCaseInsensitiveAcrossBilingualName() {
    // given
    var p = new ParameterDescriptor(
      BilingualString.of("Имя", "Name"), TypeSet.of(NUMBER), false,
      BilingualString.EMPTY, "");

    // when / then
    assertThat(p.matches("ИМЯ")).isTrue();
    assertThat(p.matches("name")).isTrue();
    assertThat(p.matches("other")).isFalse();
  }

  @Test
  void displayMethodsReturnLocalizedStrings() {
    // given
    var p = new ParameterDescriptor(
      BilingualString.of("Имя", "Name"), TypeSet.of(NUMBER), false,
      BilingualString.of("оп", "desc"), "");

    // when / then
    assertThat(p.displayName(Language.RU)).isEqualTo("Имя");
    assertThat(p.displayName(Language.EN)).isEqualTo("Name");
    assertThat(p.displayDescription(Language.RU)).isEqualTo("оп");
    assertThat(p.displayDescription(Language.EN)).isEqualTo("desc");
  }
}
