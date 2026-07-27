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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
  void compactStorageKeepsSourceOrderAcrossAppendAndPrepend() {
    // given — тип проходит все переходы компактного хранения: одиночка → массив (append)
    // → массив с вставкой в начало (prepend). Каждый источник даёт свой член,
    // порядок обхода источников должен сохраниться.
    var ref = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйКомпактныйПорядок");
    typeRegistry.registerMemberSource(ref,
      () -> List.of(MemberDescriptor.property("первый", TypeRef.UNKNOWN, "")), FileType.BSL);
    typeRegistry.registerMemberSource(ref,
      () -> List.of(MemberDescriptor.property("второй", TypeRef.UNKNOWN, "")), FileType.BSL);
    typeRegistry.registerMemberOverride(ref,
      () -> List.of(MemberDescriptor.property("вставленный", TypeRef.UNKNOWN, "")), FileType.BSL);

    // then — override встал в начало, исходные источники сохранили относительный порядок
    assertThat(typeRegistry.getMembers(ref, FileType.BSL))
      .extracting(MemberDescriptor::name)
      .containsExactly("вставленный", "первый", "второй");
  }

  @Test
  void invalidateMembersEvictsOnlyGivenType() {
    // given — два типа с изменяемыми источниками членов, оба прочитаны и мемоизированы
    var refA = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйТочечныйA");
    var refB = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйТочечныйB");
    var nameA = new AtomicReference<>("A1");
    var nameB = new AtomicReference<>("B1");
    typeRegistry.registerMemberSource(refA,
      () -> List.of(MemberDescriptor.property(nameA.get(), TypeRef.UNKNOWN, "")), FileType.BSL);
    typeRegistry.registerMemberSource(refB,
      () -> List.of(MemberDescriptor.property(nameB.get(), TypeRef.UNKNOWN, "")), FileType.BSL);
    assertThat(typeRegistry.getMembers(refA, FileType.BSL))
      .extracting(MemberDescriptor::name).containsExactly("A1");
    assertThat(typeRegistry.getMembers(refB, FileType.BSL))
      .extracting(MemberDescriptor::name).containsExactly("B1");

    // when — оба источника поменяли вывод, но инвалидируем ТОЛЬКО тип A
    nameA.set("A2");
    nameB.set("B2");
    typeRegistry.invalidateMembers(refA);

    // then — A пересобран (виден новый член), B остался из кэша: инвалидация точечная,
    // без сдвига глобальной эпохи (иначе B тоже пересобрался бы в "B2")
    assertThat(typeRegistry.getMembers(refA, FileType.BSL))
      .extracting(MemberDescriptor::name).containsExactly("A2");
    assertThat(typeRegistry.getMembers(refB, FileType.BSL))
      .extracting(MemberDescriptor::name).containsExactly("B1");
  }

  @Test
  void staleInFlightComputeIsRejectedAfterInvalidation() {
    // given — источник, который ВО ВРЕМЯ первого вычисления сам инициирует инвалидацию
    // (имитация параллельной правки, прошедшей после снятия поколения, но до записи в кэш)
    var ref = typeRegistry.intern(TypeKind.PLATFORM, "ТестовыйГонкаПоколения");
    var value = new AtomicReference<>("stale");
    var invalidateWhileComputing = new AtomicBoolean(false);
    typeRegistry.registerMemberSource(ref, () -> {
      if (invalidateWhileComputing.getAndSet(false)) {
        typeRegistry.invalidateMembers(ref);
      }
      return List.of(MemberDescriptor.property(value.get(), TypeRef.UNKNOWN, ""));
    }, FileType.BSL);

    // when — вычисление снимает поколение G, внутри него проходит инвалидация (G→G+1),
    // устаревший результат дописывается в кэш под поколением G
    invalidateWhileComputing.set(true);
    typeRegistry.getMembers(ref, FileType.BSL);
    value.set("fresh");

    // then — следующее чтение видит рассинхрон поколения и пересобирает свежий результат,
    // а не отдаёт устаревшую запись
    assertThat(typeRegistry.getMembers(ref, FileType.BSL))
      .extracting(MemberDescriptor::name).containsExactly("fresh");
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
