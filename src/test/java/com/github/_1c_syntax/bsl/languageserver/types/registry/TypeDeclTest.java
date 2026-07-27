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

import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TypeDeclTest {

  @Test
  void qualifiedNameReturnsPrimaryName() {
    // given
    var name = BilingualString.of("Массив", "Array");
    var decl = new TypePackProvider.TypeDecl(TypeKind.PLATFORM, name, List.of(),
      BilingualString.EMPTY, List.of(), List.of(), false, false,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), false);

    // when / then
    assertThat(decl.qualifiedName()).isEqualTo("Массив");
  }

  @Test
  void toRefBuildsTypeRefFromKindAndName() {
    // given
    var name = BilingualString.of("Структура");
    var decl = new TypePackProvider.TypeDecl(TypeKind.PLATFORM, name, List.of(),
      BilingualString.EMPTY, List.of(), List.of(), false, false,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), false);

    // when
    var ref = decl.toRef();

    // then
    assertThat(ref.kind()).isEqualTo(TypeKind.PLATFORM);
    assertThat(ref.qualifiedName()).isEqualTo("Структура");
  }

  @Test
  void canonicalCtorNormalizesNullFields() {
    // given / when — все опциональные поля null → подменяются на EMPTY.
    var decl = new TypePackProvider.TypeDecl(TypeKind.PRIMITIVE,
      null, List.of(),
      (BilingualString) null, List.of(), List.of(), false, false,
      (BilingualString) null, (BilingualString) null, null, false);

    // then
    assertThat(decl.name()).isSameAs(BilingualString.EMPTY);
    assertThat(decl.description()).isSameAs(BilingualString.EMPTY);
    assertThat(decl.forEachDescription()).isSameAs(BilingualString.EMPTY);
    assertThat(decl.indexAccessDescription()).isSameAs(BilingualString.EMPTY);
    assertThat(decl.typeParameters()).isEmpty();
    assertThat(decl.metadata()).isSameAs(PlatformMetadata.EMPTY);
  }

  @Test
  void compatCtorWithoutMetadataFallsBackToEmpty() {
    // given / when — компат-конструктор без metadata (JSON-паки, конфигурационные типы).
    var decl = new TypePackProvider.TypeDecl(TypeKind.PLATFORM,
      BilingualString.of("T"), List.of(),
      BilingualString.of("описание"), List.of(), List.of(), false, false,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), false);

    // then
    assertThat(decl.metadata()).isSameAs(PlatformMetadata.EMPTY);
  }

  @Test
  void canonicalCtorKeepsPageMetadata() {
    // given
    var metadata = new PlatformMetadata(
      "8.3.10", "8.3.27", List.of("ЗаменаX"),
      Set.of(Availability.SERVER), null,
      BilingualString.EMPTY, BilingualString.of("замечание"),
      List.of(BilingualString.of("пример")), List.of(BilingualString.of("см.")));

    // when
    var decl = new TypePackProvider.TypeDecl(TypeKind.PLATFORM,
      BilingualString.of("ТабличноеПоле"), List.of(),
      BilingualString.EMPTY, List.of(), List.of(), false, false,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), false, metadata);

    // then
    assertThat(decl.metadata()).isEqualTo(metadata);
    assertThat(decl.metadata().deprecatedSinceVersion()).isEqualTo("8.3.27");
  }

  @Test
  void compatCtorWithStringDescriptionsWrapsToBilingual() {
    // given / when
    var decl = new TypePackProvider.TypeDecl(TypeKind.PLATFORM,
      BilingualString.of("T"), List.of(),
      "описание", List.of(), List.of(), true, true,
      "обход", "индексатор",
      List.of("X"), false);

    // then
    assertThat(decl.description().primary()).isEqualTo("описание");
    assertThat(decl.forEachDescription().primary()).isEqualTo("обход");
    assertThat(decl.indexAccessDescription().primary()).isEqualTo("индексатор");
  }
}
