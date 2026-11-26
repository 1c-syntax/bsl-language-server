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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionTraitsIndexTest {

  private static final TypeRef SOURCE = new TypeRef(TypeKind.PLATFORM, "Источник");
  private static final TypeRef TARGET = new TypeRef(TypeKind.PLATFORM, "Получатель");
  private static final TypeRef ELEMENT = new TypeRef(TypeKind.PLATFORM, "Элемент");

  private final CollectionTraitsIndex index = new CollectionTraitsIndex();

  @Test
  void repeatedRegistrationOverwritesOnlyDeclaredTraits() {
    // given
    index.registerPack(decl(true, false, "обход", ""), SOURCE, FileType.BSL);

    // when
    index.registerPack(decl(false, true, "", "индекс"), SOURCE, FileType.BSL);
    index.registerPack(decl(false, false, "новый обход", ""), SOURCE, FileType.BSL);

    // then
    assertThat(index.supportsForEach(SOURCE, FileType.BSL)).isTrue();
    assertThat(index.supportsIndexAccess(SOURCE, FileType.BSL)).isTrue();
    assertThat(index.forEachDescription(SOURCE, FileType.BSL, Language.RU)).isEqualTo("новый обход");
    assertThat(index.indexAccessDescription(SOURCE, FileType.BSL, Language.RU)).isEqualTo("индекс");
  }

  @Test
  void inheritanceFillsOnlyTraitsTheTargetLacks() {
    // given
    index.registerPack(decl(true, true, "обход источника", "индекс источника"), SOURCE, FileType.BSL);
    index.registerPack(decl(false, false, "", "свой индекс"), TARGET, FileType.BSL);

    // when
    index.inherit(TARGET, SOURCE, FileType.BSL);

    // then
    assertThat(index.supportsForEach(TARGET, FileType.BSL)).isTrue();
    assertThat(index.supportsIndexAccess(TARGET, FileType.BSL)).isTrue();
    assertThat(index.forEachDescription(TARGET, FileType.BSL, Language.RU)).isEqualTo("обход источника");
    assertThat(index.indexAccessDescription(TARGET, FileType.BSL, Language.RU)).isEqualTo("свой индекс");
  }

  @Test
  void inheritedTraitsDoNotFollowLaterChangesOfTheSource() {
    // given
    index.setIterable(SOURCE, true, FileType.BSL);
    index.inherit(TARGET, SOURCE, FileType.BSL);

    // when
    index.setIterable(SOURCE, false, FileType.BSL);

    // then
    assertThat(index.supportsForEach(SOURCE, FileType.BSL)).isFalse();
    assertThat(index.supportsForEach(TARGET, FileType.BSL)).isTrue();
  }

  @Test
  void clearingIterabilityKeepsOtherTraits() {
    // given
    index.registerPack(decl(true, true, "обход", "индекс"), SOURCE, FileType.BSL);

    // when
    index.setIterable(SOURCE, false, FileType.BSL);

    // then
    assertThat(index.supportsForEach(SOURCE, FileType.BSL)).isFalse();
    assertThat(index.supportsIndexAccess(SOURCE, FileType.BSL)).isTrue();
    assertThat(index.forEachDescription(SOURCE, FileType.BSL, Language.RU)).isEqualTo("обход");
  }

  @Test
  void traitsAreKeptPerLanguage() {
    // when
    index.setIterable(SOURCE, true, FileType.OS);

    // then
    assertThat(index.supportsForEach(SOURCE, FileType.OS)).isTrue();
    assertThat(index.supportsForEach(SOURCE, FileType.BSL)).isFalse();
  }

  @Test
  void removalForgetsTraitsAndElementTypes() {
    // given
    index.registerPack(decl(true, true, "обход", "индекс"), SOURCE, FileType.BSL);
    index.setIterable(SOURCE, true, FileType.OS);
    index.registerDefaultElementTypes(SOURCE, List.of(ELEMENT));

    // when
    index.remove(SOURCE);

    // then
    assertThat(index.supportsForEach(SOURCE, FileType.BSL)).isFalse();
    assertThat(index.supportsForEach(SOURCE, FileType.OS)).isFalse();
    assertThat(index.supportsIndexAccess(SOURCE, FileType.BSL)).isFalse();
    assertThat(index.forEachDescription(SOURCE, FileType.BSL, Language.RU)).isEmpty();
    assertThat(index.defaultElementTypes(SOURCE, UnaryOperator.identity()).isEmpty()).isTrue();
  }

  private static TypePackProvider.TypeDecl decl(boolean forEach, boolean indexAccess,
                                                String forEachDescription, String indexAccessDescription) {
    return new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.EMPTY,
      List.of(),
      BilingualString.EMPTY,
      List.of(),
      List.of(),
      forEach,
      indexAccess,
      BilingualString.of(forEachDescription),
      BilingualString.of(indexAccessDescription),
      List.of(),
      false
    );
  }
}
