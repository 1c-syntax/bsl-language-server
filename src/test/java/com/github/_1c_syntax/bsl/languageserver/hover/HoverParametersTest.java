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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HoverParametersTest {

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Test
  void typesLabelJoinsNonEmptyDisplayNames() {
    var types = TypeSet.of(NUMBER, STRING);

    assertThat(HoverParameters.typesLabel(types, TypeRef::qualifiedName)).isEqualTo("Число | Строка");
    assertThat(HoverParameters.typesLabel(types, ref -> ref == NUMBER ? "" : "Строка")).isEqualTo("Строка");
    assertThat(HoverParameters.typesLabel(TypeSet.EMPTY, TypeRef::qualifiedName)).isEmpty();
  }

  @Test
  void parameterLineHasDefaultValueAndDescription() {
    var parameter = new ParameterDescriptor(
      BilingualString.of("Значение", "Value"), TypeSet.of(NUMBER), true,
      BilingualString.of("Описание", "Description"), "0", false);
    var sb = new StringBuilder();

    HoverParameters.appendParameter(sb, "Значение", "Число", parameter, Language.RU);

    assertThat(sb).hasToString("- `Значение`: Число? _= 0_ — Описание\n");
  }

  @Test
  void parameterLineWithoutDefaultAndDescription() {
    var parameter = new ParameterDescriptor(
      BilingualString.of("Значение"), TypeSet.EMPTY, false, BilingualString.EMPTY, "", false);
    var sb = new StringBuilder();

    HoverParameters.appendParameter(sb, "Значение", "", parameter, Language.RU);

    assertThat(sb).hasToString("- `Значение`\n");
  }
}
