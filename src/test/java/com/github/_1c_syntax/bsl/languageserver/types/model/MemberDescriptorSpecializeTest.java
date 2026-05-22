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
package com.github._1c_syntax.bsl.languageserver.types.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemberDescriptorSpecializeTest {

  private static final TypeRef CATALOG_REF_GENERIC = new TypeRef(TypeKind.PLATFORM, "СправочникСсылка.<Имя справочника>");
  private static final TypeRef CATALOG_OBJECT_GENERIC = new TypeRef(TypeKind.PLATFORM, "СправочникОбъект.<Имя справочника>");
  private static final TypeRef UNDEFINED = new TypeRef(TypeKind.PRIMITIVE, "Неопределено");

  @Test
  void specializeMethodReturnTypesAndSignatureReturnTypes() {
    // ПолучитьОбъект() возвращает (СправочникОбъект.<...> | Неопределено).
    // Подстановка mdName в обе позиции.
    var sig = new SignatureDescriptor(
      List.of(),
      TypeSet.of(List.of(CATALOG_OBJECT_GENERIC, UNDEFINED)),
      "");
    var member = new MemberDescriptor(
      "ПолучитьОбъект",
      MemberKind.METHOD,
      "",
      TypeSet.of(List.of(CATALOG_OBJECT_GENERIC, UNDEFINED)),
      List.of(sig),
      null,
      false,
      PlatformMetadata.EMPTY);

    var specialized = member.specialize(Map.of("Имя справочника", "X"));
    var returnNames = specialized.returnTypes().refs().stream().map(TypeRef::qualifiedName).toList();
    assertThat(returnNames).containsExactlyInAnyOrder("СправочникОбъект.X", "Неопределено");

    var sigReturnNames = specialized.signatures().get(0).returnTypes().refs().stream()
      .map(TypeRef::qualifiedName).toList();
    assertThat(sigReturnNames).containsExactlyInAnyOrder("СправочникОбъект.X", "Неопределено");
  }

  @Test
  void specializeIsNoOpWhenNoPlaceholderInDescriptor() {
    var member = MemberDescriptor.property("Имя", new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    var specialized = member.specialize(Map.of("Имя справочника", "X"));
    assertThat(specialized).isSameAs(member);
  }

  @Test
  void specializeIsNoOpWithEmptyBindings() {
    var member = MemberDescriptor.property("Ссылка", CATALOG_REF_GENERIC);
    var specialized = member.specialize(Map.of());
    assertThat(specialized).isSameAs(member);
  }

  @Test
  void specializePreservesUnrelatedFields() {
    var meta = new PlatformMetadata("8.3.10", "", List.of(), java.util.Set.of(), null, "", "", List.of(), List.of());
    var member = new MemberDescriptor(
      "Ссылка",
      MemberKind.PROPERTY,
      "описание",
      TypeSet.of(CATALOG_REF_GENERIC),
      List.of(),
      null,
      false,
      meta);

    var specialized = member.specialize(Map.of("Имя справочника", "X"));
    assertThat(specialized.name()).isEqualTo("Ссылка");
    assertThat(specialized.description()).isEqualTo("описание");
    assertThat(specialized.metadata()).isSameAs(meta);
    assertThat(specialized.returnTypes().refs().iterator().next().qualifiedName())
      .isEqualTo("СправочникСсылка.X");
  }
}
