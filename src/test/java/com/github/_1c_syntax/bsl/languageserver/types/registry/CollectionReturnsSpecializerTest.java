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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class CollectionReturnsSpecializerTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void registeredCollectionReturnsItsOwnRow() {
    // given — обобщённая табличная часть платформы и своя пара «коллекция — строка».
    var genericCollection = typeRegistry.resolve("Табличная часть").orElseThrow();
    var genericRow = typeRegistry.resolve("Строка табличной части").orElseThrow();
    var collection = typeRegistry.registerConfigurationType("ТестоваяКоллекция.Товары");
    var row = typeRegistry.registerConfigurationType("ТестоваяСтрока.Товары");

    // when
    CollectionReturnsSpecializer.register(typeRegistry, collection, genericCollection, genericRow, row,
      List::of);

    // then — Добавить() возвращает строку этой коллекции, а не обобщённую.
    var add = typeRegistry.getMembers(collection, FileType.BSL).stream()
      .filter(member -> "Добавить".equals(member.name()))
      .findFirst();
    assertThat(add).map(MemberDescriptor::returnType).contains(row);
  }

  @Test
  void registeredTypedCollectionIteratesOverItsOwnRow() {
    // given
    var genericCollection = typeRegistry.resolve("Табличная часть").orElseThrow();
    var genericRow = typeRegistry.resolve("Строка табличной части").orElseThrow();
    var collection = typeRegistry.registerConfigurationType("ТипизированнаяКоллекция.Товары");
    var row = typeRegistry.registerConfigurationType("ТипизированнаяСтрока.Товары");

    // when
    CollectionReturnsSpecializer.registerCollection(typeRegistry, collection, genericCollection, genericRow, row,
      List::of);

    // then — элемент — своя строка, а не унаследованная обобщённая; обход и методы — от обобщённой.
    assertThat(typeRegistry.getDefaultElementTypes(collection).refs()).containsExactly(row);
    assertThat(typeRegistry.supportsForEach(collection, FileType.BSL))
      .isEqualTo(typeRegistry.supportsForEach(genericCollection, FileType.BSL));
    var add = typeRegistry.getMembers(collection, FileType.BSL).stream()
      .filter(member -> "Добавить".equals(member.name()))
      .findFirst();
    assertThat(add).map(MemberDescriptor::returnType).contains(row);
  }
}
