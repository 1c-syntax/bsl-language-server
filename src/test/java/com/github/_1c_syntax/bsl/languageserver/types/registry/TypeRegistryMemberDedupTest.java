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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TypeRegistry#getMembers} объединяет members из нескольких источников,
 * дедуплицируя по паре ({@link MemberKind}, имя без учёта регистра) — метод и
 * свойство с одинаковым именем должны сосуществовать (у каждого вида — свой
 * "мешок" дедупликации), а не вытеснять друг друга.
 */
class TypeRegistryMemberDedupTest {

  private TypeRegistry typeRegistry;

  @BeforeEach
  void setUp() {
    typeRegistry = new TypeRegistry(List.of(), new MemberMetadataIndex());
    typeRegistry.bootstrap();
  }

  @Test
  void propertyAndMethodWithSameNameBothSurvive() {
    var ref = typeRegistry.registerConfigurationType("ТестовыйТипСОдноимённымиЧленами");
    var property = MemberDescriptor.property("Записать", TypeRef.UNKNOWN);
    var method = MemberDescriptor.method("Записать");
    typeRegistry.registerMemberSource(ref, () -> List.of(property), FileType.BSL);
    typeRegistry.registerMemberSource(ref, () -> List.of(method), FileType.BSL);

    var members = typeRegistry.getMembers(ref, FileType.BSL);

    assertThat(members)
      .as("свойство и метод с одинаковым именем — разные члены, оба должны присутствовать")
      .extracting(MemberDescriptor::kind)
      .contains(MemberKind.PROPERTY, MemberKind.METHOD);
  }

  @Test
  void twoMembersOfSameKindAndNameStillDedupeToTheFirstOne() {
    var ref = typeRegistry.registerConfigurationType("ТестовыйТипСДублемСвойства");
    var first = MemberDescriptor.property("Х", TypeRef.UNKNOWN, "первый источник");
    var second = MemberDescriptor.property("Х", TypeRef.UNKNOWN, "второй источник");
    typeRegistry.registerMemberSource(ref, () -> List.of(first), FileType.BSL);
    typeRegistry.registerMemberSource(ref, () -> List.of(second), FileType.BSL);

    var members = typeRegistry.getMembers(ref, FileType.BSL);
    var matching = members.stream().filter(m -> m.matches("Х")).toList();

    assertThat(matching)
      .as("два члена ОДНОГО вида с одинаковым именем по-прежнему дедуплицируются — выигрывает первый источник")
      .hasSize(1);
    assertThat(matching.getFirst().description()).isEqualTo("первый источник");
  }

  @Test
  void memberKeyOrdersByKindThenName() {
    var propertyA = new TypeRegistry.MemberKey(MemberKind.PROPERTY, "а");
    var propertyB = new TypeRegistry.MemberKey(MemberKind.PROPERTY, "б");
    var methodA = new TypeRegistry.MemberKey(MemberKind.METHOD, "а");

    // Одинаковый вид — сравнение по имени.
    assertThat(propertyA).isLessThan(propertyB);
    // Разный вид — сравнение по виду члена (имя одинаковое).
    assertThat(propertyA.compareTo(methodA)).isNotZero();
    // Рефлексивность.
    assertThat(propertyA.compareTo(propertyA)).isZero();
  }
}
