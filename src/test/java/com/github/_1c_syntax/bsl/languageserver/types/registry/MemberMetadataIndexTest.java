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

import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты на index/pre-filter'ы {@link MemberMetadataIndex}: двуязычные
 * имена, версионная пометка, режим доступа READ, фильтр на «нет ни одной
 * метаданной» (early-return).
 */
class MemberMetadataIndexTest {

  private static final TypeRef OWNER = new TypeRef(TypeKind.PLATFORM, "СистемнаяИнформация");
  private static final TypeRef OTHER = new TypeRef(TypeKind.PLATFORM, "Массив");

  @Test
  void emptyIndexReportsNoFlags() {
    var index = new MemberMetadataIndex();

    assertThat(index.hasAnyReadOnly()).isFalse();
    assertThat(index.isReadOnlyName("Имя")).isFalse();
    assertThat(index.isReadOnly(OWNER, "Имя")).isFalse();
    assertThat(index.isVersionedName("Имя")).isFalse();
  }

  @Test
  void memberWithoutMetadataIsSkipped() {

    // given — обычный метод без deprecation/accessMode.
    var index = new MemberMetadataIndex();
    var member = MemberDescriptor.method("ПростойМетод");

    // when
    index.index(OWNER, member);

    // then — early-return: ни один индекс не пополнился.
    assertThat(index.isVersionedName("ПростойМетод")).isFalse();
    assertThat(index.hasAnyReadOnly()).isFalse();
  }

  @Test
  void versionedMemberIndexesBothLocales() {

    // given — bilingual property с deprecatedSinceVersion.
    var index = new MemberMetadataIndex();
    var metadata = deprecatedAlwaysMetadata();
    var member = MemberDescriptor.property("ПолучитьПеременнуюСреды", OTHER, "")
      .withBilingualName(BilingualString.of("ПолучитьПеременнуюСреды", "GetEnvironmentVariable"))
      .withMetadata(metadata);

    // when
    index.index(OWNER, member);

    // then — обе локали попали в versionedNames (регистронезависимо).
    assertThat(index.isVersionedName("ПолучитьПеременнуюСреды")).isTrue();
    assertThat(index.isVersionedName("получитьпеременнуюсреды")).isTrue();
    assertThat(index.isVersionedName("GetEnvironmentVariable")).isTrue();
    assertThat(index.isVersionedName("getenvironmentvariable")).isTrue();
    assertThat(index.isVersionedName("ДругоеИмя")).isFalse();
    assertThat(index.hasAnyReadOnly()).isFalse();
  }

  @Test
  void readOnlyMemberPopulatesPerTypeAndFlatIndexes() {

    // given — read-only property с двуязычным именем.
    var index = new MemberMetadataIndex();
    var member = MemberDescriptor.property("Версия", OTHER, "")
      .withBilingualName(BilingualString.of("Версия", "Version"))
      .withMetadata(readOnlyMetadata());

    // when
    index.index(OWNER, member);

    // then
    assertThat(index.hasAnyReadOnly()).isTrue();
    assertThat(index.isReadOnlyName("Версия")).isTrue();
    assertThat(index.isReadOnlyName("Version")).isTrue();
    assertThat(index.isReadOnly(OWNER, "версия")).isTrue();
    assertThat(index.isReadOnly(OWNER, "VERSION")).isTrue();
    // другой тип-владелец не задет
    assertThat(index.isReadOnly(OTHER, "Версия")).isFalse();
  }

  @Test
  void monolingualNameSkipsEmptyLocale() {

    // given — одноязычное (только ru) имя.
    var index = new MemberMetadataIndex();
    var member = MemberDescriptor.property("ТолькоРусское", OTHER, "")
      .withMetadata(readOnlyMetadata());

    // when
    index.index(OWNER, member);

    // then — пустая en-локаль не порождает мусорной записи.
    assertThat(index.isReadOnlyName("ТолькоРусское")).isTrue();
    assertThat(index.isReadOnlyName("")).isFalse();
  }

  private static PlatformMetadata deprecatedAlwaysMetadata() {
    return new PlatformMetadata("", "*", java.util.List.of(),
      java.util.Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY,
      java.util.List.of(), java.util.List.of());
  }

  private static PlatformMetadata readOnlyMetadata() {
    return new PlatformMetadata("", "", java.util.List.of(),
      java.util.Set.of(), AccessMode.READ,
      BilingualString.EMPTY, BilingualString.EMPTY,
      java.util.List.of(), java.util.List.of());
  }
}
