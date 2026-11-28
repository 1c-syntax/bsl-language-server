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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.types.MultiLanguageString;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormPlatformTypesNamedPropertiesTest {

  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  private record Child(String name, MultiLanguageString title) {
  }

  @Test
  void childrenBecomePropertiesOncePerNameIgnoringCase() {
    // given — пустое имя, повтор имени в другом регистре
    var children = List.of(
      new Child("Товары", MultiLanguageString.create("ru", "Список товаров")),
      new Child("", MultiLanguageString.EMPTY),
      new Child("ТОВАРЫ", MultiLanguageString.create("ru", "Дубль")),
      new Child("Сумма", MultiLanguageString.EMPTY));

    // when
    var members = FormPlatformTypes.namedProperties(children, Child::name, Child::title,
      child -> TypeSet.of(STRING));

    // then — первое объявление имени выигрывает, пустые имена пропускаются
    assertThat(members).extracting(MemberDescriptor::name).containsExactly("Товары", "Сумма");
    var goods = members.getFirst();
    assertThat(goods.bilingualName()).isEqualTo(BilingualString.of("Товары", "Товары"));
    assertThat(goods.description()).isEqualTo("Список товаров");
    assertThat(goods.returnType()).isEqualTo(STRING);
  }
}
