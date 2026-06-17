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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Коллекционные/enum-признаки типа разрезаны по языку файла: одно и то же имя
 * в BSL и OneScript — разные типы с разной природой, поэтому
 * {@code isEnumType}/{@code supportsForEach}/{@code supportsIndexAccess} и их
 * описания, выставленные источником одного языка, не должны протекать в другой.
 */
class TypeRegistryCollectionTraitsPerLanguageTest {

  private static final TypeRef REF = new TypeRef(TypeKind.PLATFORM, "ТестоваяКоллекция");

  @Test
  void collectionTraitsRegisteredForOneLanguageDoNotLeakToAnother() {
    // given: тип-коллекция-перечисление с описаниями, заявленный ТОЛЬКО для BSL
    var decl = new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.of("ТестоваяКоллекция"),
      List.of(),
      "",
      List.of(),
      List.of(),
      true,
      true,
      "обход BSL",
      "индекс BSL",
      List.of(),
      true
    );
    var registry = new TypeRegistry(List.of(bslProviderOf(decl)), Mockito.mock(MemberMetadataIndex.class));

    // when
    registry.bootstrap();

    // then: в BSL признаки видны, в OS — нет
    assertThat(registry.isEnumType(REF, FileType.BSL)).isTrue();
    assertThat(registry.isEnumType(REF, FileType.OS)).isFalse();

    assertThat(registry.supportsForEach(REF, FileType.BSL)).isTrue();
    assertThat(registry.supportsForEach(REF, FileType.OS)).isFalse();

    assertThat(registry.supportsIndexAccess(REF, FileType.BSL)).isTrue();
    assertThat(registry.supportsIndexAccess(REF, FileType.OS)).isFalse();

    assertThat(registry.getForEachDescription(REF, FileType.BSL, Language.RU)).isEqualTo("обход BSL");
    assertThat(registry.getForEachDescription(REF, FileType.OS, Language.RU)).isEmpty();

    assertThat(registry.getIndexAccessDescription(REF, FileType.BSL, Language.RU)).isEqualTo("индекс BSL");
    assertThat(registry.getIndexAccessDescription(REF, FileType.OS, Language.RU)).isEmpty();
  }

  private static PlatformTypesProvider bslProviderOf(TypePackProvider.TypeDecl decl) {
    return new PlatformTypesProvider() {
      @Override
      public FileType getFileType() {
        return FileType.BSL;
      }

      @Override
      public Collection<TypePackProvider.TypeDecl> getTypes() {
        return List.of(decl);
      }
    };
  }
}
