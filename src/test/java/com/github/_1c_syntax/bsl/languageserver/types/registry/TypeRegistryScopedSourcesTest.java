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
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тип с одним qualifiedName может иметь РАЗНЫЕ члены, конструкторы и описания
 * в BSL и OS — все три измерения должны фильтроваться по {@link FileType}.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class TypeRegistryScopedSourcesTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void membersAreScopedByFileType() {
    var ref = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйДвуязычный");
    typeRegistry.registerMemberSource(ref,
      () -> List.of(MemberDescriptor.property("ТолькоBsl", TypeRef.UNKNOWN, "bsl")),
      FileType.BSL);
    typeRegistry.registerMemberSource(ref,
      () -> List.of(MemberDescriptor.property("ТолькоOs", TypeRef.UNKNOWN, "os")),
      FileType.OS);

    assertThat(typeRegistry.getMembers(ref, FileType.BSL))
      .extracting(MemberDescriptor::name)
      .containsExactly("ТолькоBsl");
    assertThat(typeRegistry.getMembers(ref, FileType.OS))
      .extracting(MemberDescriptor::name)
      .containsExactly("ТолькоOs");
  }

  @Test
  void constructorsAreScopedByFileType() {
    var ref = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйДвуязычныйКонструктор");
    var bslCtor = new SignatureDescriptor(List.of(), ref, "bsl-ctor");
    var osCtor = new SignatureDescriptor(List.of(), ref, "os-ctor");

    typeRegistry.registerConstructors(ref, List.of(bslCtor), FileType.BSL);
    typeRegistry.registerConstructors(ref, List.of(osCtor), FileType.OS);

    assertThat(typeRegistry.getConstructors(ref, FileType.BSL))
      .extracting(SignatureDescriptor::description)
      .containsExactly("bsl-ctor");
    assertThat(typeRegistry.getConstructors(ref, FileType.OS))
      .extracting(SignatureDescriptor::description)
      .containsExactly("os-ctor");
  }

  @Test
  void descriptionIsScopedByFileType() {
    var ref = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйДвуязычноеОписание");
    typeRegistry.registerDescription(ref, "описание для BSL", FileType.BSL);
    typeRegistry.registerDescription(ref, "описание для OS", FileType.OS);

    assertThat(typeRegistry.getDescription(ref, FileType.BSL)).isEqualTo("описание для BSL");
    assertThat(typeRegistry.getDescription(ref, FileType.OS)).isEqualTo("описание для OS");
  }

  @Test
  void sourceSharedByBothLanguagesIsRegisteredPerFileType() {
    // given — сущность, видимая в обоих языках, регистрируется по разу на язык
    var ref = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйОбщийИсточник");
    for (var fileType : FileType.values()) {
      typeRegistry.registerMemberSource(ref,
        () -> List.of(MemberDescriptor.property("Общий", TypeRef.UNKNOWN, "")),
        fileType);
      typeRegistry.registerDescription(ref, "общее описание", fileType);
      typeRegistry.registerConstructors(ref,
        List.of(new SignatureDescriptor(List.of(), ref, "общий")), fileType);
    }

    // then
    assertThat(typeRegistry.getMembers(ref, FileType.BSL))
      .extracting(MemberDescriptor::name).containsExactly("Общий");
    assertThat(typeRegistry.getMembers(ref, FileType.OS))
      .extracting(MemberDescriptor::name).containsExactly("Общий");
    assertThat(typeRegistry.getDescription(ref, FileType.BSL)).isEqualTo("общее описание");
    assertThat(typeRegistry.getDescription(ref, FileType.OS)).isEqualTo("общее описание");
    assertThat(typeRegistry.getConstructors(ref, FileType.BSL)).hasSize(1);
    assertThat(typeRegistry.getConstructors(ref, FileType.OS)).hasSize(1);
  }

  @Test
  void registerMemberOverrideInvalidatesMembersCache() {
    // given — члены типа уже прочитаны и мемоизированы
    var ref = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйКэшОверрайда");
    typeRegistry.registerMemberSource(ref,
      () -> List.of(MemberDescriptor.property("Базовый", TypeRef.UNKNOWN, "")),
      FileType.BSL);
    assertThat(typeRegistry.getMembers(ref, FileType.BSL))
      .extracting(MemberDescriptor::name).containsExactly("Базовый");

    // when — override регистрируется ПОСЛЕ первого чтения
    typeRegistry.registerMemberOverride(ref,
      () -> List.of(MemberDescriptor.property("Переопределённый", TypeRef.UNKNOWN, "")),
      FileType.BSL);

    // then — кэш инвалидирован, override виден без рестарта
    assertThat(typeRegistry.getMembers(ref, FileType.BSL))
      .extracting(MemberDescriptor::name)
      .containsExactly("Переопределённый", "Базовый");
  }
}
