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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регресс-тесты к #4054: члены типов и конструкторы, существующих и в BSL,
 * и в OneScript, уже корректно фильтруются по языковому скоупу источника
 * ({@code computeMembers}/{@code getConstructors} со scope-фильтром) —
 * фиксируем это поведение, чтобы его не сломали.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class TypeRegistryLanguageScopedMembersTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void osOnlyMemberOfSharedTypeIsVisibleOnlyInOsFiles() {
    // given — ВыбратьСтроку есть только в OneScript-наборе ТаблицаЗначений
    var ref = typeRegistry.resolve("ТаблицаЗначений").orElseThrow();

    // when
    var osMembers = typeRegistry.getMembers(ref, FileType.OS);
    var bslMembers = typeRegistry.getMembers(ref, FileType.BSL);

    // then
    assertThat(osMembers).extracting(MemberDescriptor::name)
      .anyMatch("ВыбратьСтроку"::equalsIgnoreCase);
    assertThat(bslMembers).extracting(MemberDescriptor::name)
      .noneMatch("ВыбратьСтроку"::equalsIgnoreCase);
  }

  @Test
  void osOnlyEnumValueIsVisibleOnlyInOsFiles() {
    // given — значение UTF8NoBOM есть только в OneScript-наборе КодировкаТекста
    var ref = typeRegistry.resolve("КодировкаТекста").orElseThrow();

    // when
    var osMembers = typeRegistry.getMembers(ref, FileType.OS);
    var bslMembers = typeRegistry.getMembers(ref, FileType.BSL);

    // then
    assertThat(osMembers).extracting(MemberDescriptor::name)
      .anyMatch("UTF8NoBOM"::equalsIgnoreCase);
    assertThat(bslMembers).extracting(MemberDescriptor::name)
      .noneMatch("UTF8NoBOM"::equalsIgnoreCase);
  }

  @Test
  void sharedMemberIsVisibleInBothFileTypes() {
    // given — Добавить есть в обоих наборах ТаблицаЗначений
    var ref = typeRegistry.resolve("ТаблицаЗначений").orElseThrow();

    // when / then
    assertThat(typeRegistry.getMembers(ref, FileType.BSL)).extracting(MemberDescriptor::name)
      .anyMatch("Добавить"::equalsIgnoreCase);
    assertThat(typeRegistry.getMembers(ref, FileType.OS)).extracting(MemberDescriptor::name)
      .anyMatch("Добавить"::equalsIgnoreCase);
  }

  @Test
  void constructorsOfSharedTypeAreAvailableInBothFileTypes() {
    // given — у Массив constructors объявлены в обоих наборах
    var ref = typeRegistry.resolve("Массив").orElseThrow();

    // when / then
    assertThat(typeRegistry.getConstructors(ref, FileType.BSL)).isNotEmpty();
    assertThat(typeRegistry.getConstructors(ref, FileType.OS)).isNotEmpty();
  }
}
