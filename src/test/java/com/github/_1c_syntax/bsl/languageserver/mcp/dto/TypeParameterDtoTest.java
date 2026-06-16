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
package com.github._1c_syntax.bsl.languageserver.mcp.dto;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeParameterDtoTest {

  private static final TypeRef STRING_TYPE = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Test
  void mapsRequiredFields() {
    var parameter = new ParameterDescriptor("Шаблон", TypeSet.of(STRING_TYPE), false, "Шаблон строки");

    var dto = TypeParameterDto.from(parameter, Language.RU);

    assertThat(dto.name()).isEqualTo("Шаблон");
    assertThat(dto.types()).containsExactly("Строка");
    assertThat(dto.optional()).isFalse();
    assertThat(dto.variadic()).isFalse();
    assertThat(dto.defaultValue()).isNull();
    assertThat(dto.description()).isEqualTo("Шаблон строки");
  }

  @Test
  void carriesVariadicFlag() {
    var parameter = new ParameterDescriptor("Параметры", TypeSet.EMPTY, true, "")
      .withVariadic(true);

    var dto = TypeParameterDto.from(parameter, Language.RU);

    assertThat(dto.variadic()).isTrue();
    assertThat(dto.optional()).isTrue();
    assertThat(dto.description()).isNull();
  }

  @Test
  void exposesDefaultValueWhenPresent() {
    var parameter = new ParameterDescriptor("Кодировка", TypeSet.of(STRING_TYPE), true, "Кодировка", "UTF-8");

    var dto = TypeParameterDto.from(parameter, Language.RU);

    assertThat(dto.defaultValue()).isEqualTo("UTF-8");
    assertThat(dto.optional()).isTrue();
  }

  @Test
  void picksEnglishLocaleForBilingualNameAndDescription() {
    var parameter = new ParameterDescriptor(
      BilingualString.of("Шаблон", "Template"),
      TypeSet.of(STRING_TYPE),
      false,
      BilingualString.of("Шаблон строки", "String template"),
      ""
    );

    var dto = TypeParameterDto.from(parameter, Language.EN);

    assertThat(dto.name()).isEqualTo("Template");
    assertThat(dto.description()).isEqualTo("String template");
  }

  @Test
  void blankDefaultValueAndDescriptionBecomeNull() {
    var parameter = new ParameterDescriptor("Параметр", TypeSet.EMPTY, false, "", "");

    var dto = TypeParameterDto.from(parameter, Language.RU);

    assertThat(dto.defaultValue()).isNull();
    assertThat(dto.description()).isNull();
  }
}
