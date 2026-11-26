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
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Отбор свойств, которым доопределяется тип
 * ({@link FormItemTypesRegistrar#memberTypeName}) — без Spring и синтакс-помощника.
 * Словарь {@link FormPlatformTypes#TYPELESS_MEMBER_TYPES} задан русскими именами, а у
 * платформенного члена написаний два, поэтому проверяется в первую очередь сопоставление.
 */
class FormItemTypesRegistrarHelpersTest {

  private static final Map<String, String> DICTIONARY = Map.of("АвтоОтметкаНезаполненного", "Булево");

  @Test
  void typelessPropertyIsMatchedByItsRussianName() {
    assertThat(FormItemTypesRegistrar.memberTypeName(DICTIONARY, typeless("АвтоОтметкаНезаполненного", "AutoMarkIncomplete")))
      .isEqualTo("Булево");
  }

  @Test
  void typelessPropertyIsMatchedByItsEnglishNameToo() {
    // Словарь русский, а primary-написание у члена может оказаться английским —
    // сопоставление идёт по обеим локалям, а не по одной строке.
    assertThat(FormItemTypesRegistrar.memberTypeName(DICTIONARY, typeless("", "АвтоОтметкаНезаполненного")))
      .isEqualTo("Булево");
  }

  @Test
  void matchingIgnoresCase() {
    assertThat(FormItemTypesRegistrar.memberTypeName(DICTIONARY, typeless("автоотметканезаполненного", "")))
      .isEqualTo("Булево");
  }

  @Test
  void propertyOutsideTheDictionaryIsLeftAlone() {
    assertThat(FormItemTypesRegistrar.memberTypeName(DICTIONARY, typeless("Заголовок", "Title"))).isNull();
  }

  @Test
  void propertyWithDeclaredTypeKeepsThePlatformDeclaration() {
    var declared = MemberDescriptor.property("АвтоОтметкаНезаполненного",
      new TypeRef(TypeKind.PRIMITIVE, "Строка"));

    assertThat(FormItemTypesRegistrar.memberTypeName(DICTIONARY, declared))
      .as("платформа тип объявила — доопределять нечего, даже если он неожиданный")
      .isNull();
  }

  @Test
  void memberOfAnotherKindIsNotAProperty() {
    var event = MemberDescriptor.event("АвтоОтметкаНезаполненного", "", List.of(SignatureDescriptor.EMPTY));

    assertThat(FormItemTypesRegistrar.memberTypeName(DICTIONARY, event)).isNull();
  }

  /** Свойство, у которого платформа тип не объявила. */
  private static MemberDescriptor typeless(String ru, String en) {
    return MemberDescriptor.property(ru.isEmpty() ? en : ru, TypeSet.EMPTY, "")
      .withBilingualName(BilingualString.of(ru, en));
  }
}
