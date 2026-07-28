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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code registerSpecialization} переносит на специализацию только члены, а
 * коллекционные свойства приходят из {@code TypeDecl} и ей не достаются. Из-за этого
 * специализация типа-коллекции переставала обходиться {@code Для Каждого} и
 * индексироваться. {@code inheritCollectionTraits} закрывает этот разрыв.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class TypeRegistryCollectionTraitsInheritanceTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void specializationLosesCollectionTraitsUntilTheyAreInherited() {
    var source = typeRegistry.resolve("СписокЗначений").orElseThrow();
    assertThat(typeRegistry.supportsForEach(source, FileType.BSL)).isTrue();
    assertThat(typeRegistry.getDefaultElementTypes(source)).isNotNull();

    var specialized = typeRegistry.intern(TypeKind.PLATFORM, "СписокЗначений.Специализация");
    typeRegistry.registerSpecialization(specialized, source, Map.of(), FileType.BSL);

    assertThat(typeRegistry.supportsForEach(specialized, FileType.BSL))
      .as("сама специализация коллекционных свойств не получает")
      .isFalse();

    typeRegistry.inheritCollectionTraits(specialized, source, FileType.BSL);

    assertThat(typeRegistry.supportsForEach(specialized, FileType.BSL)).isTrue();
    assertThat(typeRegistry.supportsIndexAccess(specialized, FileType.BSL))
      .isEqualTo(typeRegistry.supportsIndexAccess(source, FileType.BSL));
    assertThat(typeRegistry.getDefaultElementTypes(specialized))
      .isEqualTo(typeRegistry.getDefaultElementTypes(source));
  }

  @Test
  void ownTraitsOfTargetWin() {
    // Наследование не перетирает то, что специализация объявила сама:
    // собственная регистрация приоритетнее унаследованной.
    var source = typeRegistry.resolve("СписокЗначений").orElseThrow();
    var target = typeRegistry.intern(TypeKind.PLATFORM, "СписокЗначений.СобственныйПризнак");
    typeRegistry.setUserTypeIterable(target, true, FileType.OS);

    typeRegistry.inheritCollectionTraits(target, source, FileType.OS);

    assertThat(typeRegistry.supportsForEach(target, FileType.OS)).isTrue();
  }

  @Test
  void selfInheritanceIsNoOp() {
    var source = typeRegistry.resolve("СписокЗначений").orElseThrow();
    var before = typeRegistry.getDefaultElementTypes(source);

    typeRegistry.inheritCollectionTraits(source, source, FileType.BSL);

    assertThat(typeRegistry.getDefaultElementTypes(source)).isEqualTo(before);
  }
}
