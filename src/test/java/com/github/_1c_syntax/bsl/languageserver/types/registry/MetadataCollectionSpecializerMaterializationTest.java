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
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.MD;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetadataCollectionSpecializerMaterializationTest {

  private final TypeRef base = new TypeRef(TypeKind.PLATFORM, "Коллекция");
  private final TypeRef element = new TypeRef(TypeKind.PLATFORM, "Элемент");
  private final MemberDescriptor first = MemberDescriptor.genericProperty("<Первый>", element, "Первый шаблон");
  private final MemberDescriptor second = MemberDescriptor.genericProperty("<Второй>", element, "Второй шаблон");
  private final MemberDescriptor count = MemberDescriptor.method("Количество");
  private final MemberDescriptor get = MemberDescriptor.method("Получить");
  private final MemberDescriptor find = MemberDescriptor.method("Найти");

  @Test
  void groupComputesChildDataOnceAndKeepsTemplateOrder() {
    var registry = registry(List.of(first, count, second, get));
    var goods = namedChild("Товары");
    var services = namedChild("Услуги");
    var blank = namedChild(" ");

    var result = MetadataCollectionSpecializer.buildGroupCollectionMembers(
      registry, base, element, List.of(goods, blank, services));

    assertThat(result).containsExactly(
      materialized(first, "Товары", "Элемент.Товары"),
      materialized(first, "Услуги", "Элемент.Услуги"), count,
      materialized(second, "Товары", "Элемент.Товары"),
      materialized(second, "Услуги", "Элемент.Услуги"),
      MetadataCollectionSpecializer.withElementReturnType(get, TypeSet.of(element)));
    verify(goods).getName();
    verify(services).getName();
    verify(blank).getName();
    verify(registry).intern(TypeKind.PLATFORM, "Элемент.Товары");
    verify(registry).intern(TypeKind.PLATFORM, "Элемент.Услуги");
    assertThat(result.get(0).bilingualName()).isSameAs(result.get(3).bilingualName());
    assertThat(result.get(0).returnTypes()).isSameAs(result.get(3).returnTypes());
  }

  @Test
  void groupWithoutGenericTemplateDoesNotReadChildren() {
    var registry = registry(List.of(count, get));
    var child = mock(MD.class);

    var result = MetadataCollectionSpecializer.buildGroupCollectionMembers(
      registry, base, element, List.of(child));

    assertThat(result).containsExactly(count,
      MetadataCollectionSpecializer.withElementReturnType(get, TypeSet.of(element)));
    verifyNoInteractions(child);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void ownerComputesChildTypesOnceForTemplatesAndMethods(boolean hasTemplates) {
    var registry = registry(hasTemplates
      ? List.of(first, get, count, second, find) : List.of(get, count, find));
    var goods = namedChild("Товары");
    var services = namedChild("Услуги");
    var children = List.of(
      MetadataCollectionSpecializer.ChildName.of("Товары", goods),
      MetadataCollectionSpecializer.ChildName.of("Услуги", services));
    var types = TypeSet.of(new TypeRef(TypeKind.PLATFORM, "Элемент.Владелец.Товары"))
      .union(TypeSet.of(new TypeRef(TypeKind.PLATFORM, "Элемент.Владелец.Услуги")));

    var result = MetadataCollectionSpecializer.buildPerOwnerCollectionMembers(
      registry, base, element, children, "Владелец");

    if (hasTemplates) {
      assertThat(result).containsExactly(
        materialized(first, "Товары", "Элемент.Владелец.Товары"),
        materialized(first, "Услуги", "Элемент.Владелец.Услуги"),
        MetadataCollectionSpecializer.withElementReturnType(get, types), count,
        materialized(second, "Товары", "Элемент.Владелец.Товары"),
        materialized(second, "Услуги", "Элемент.Владелец.Услуги"),
        MetadataCollectionSpecializer.withElementReturnType(find, types));
    } else {
      assertThat(result).containsExactly(
        MetadataCollectionSpecializer.withElementReturnType(get, types), count,
        MetadataCollectionSpecializer.withElementReturnType(find, types),
        MemberDescriptor.property("Товары", new TypeRef(TypeKind.PLATFORM, "Элемент.Владелец.Товары")),
        MemberDescriptor.property("Услуги", new TypeRef(TypeKind.PLATFORM, "Элемент.Владелец.Услуги")));
    }
    verify(goods).getName();
    verify(services).getName();
    verify(registry).intern(TypeKind.PLATFORM, "Элемент.Владелец.Товары");
    verify(registry).intern(TypeKind.PLATFORM, "Элемент.Владелец.Услуги");
    if (hasTemplates) {
      assertThat(result.get(0).returnTypes()).isSameAs(result.get(4).returnTypes());
    }
  }

  @Test
  void emptyOwnerCollectionKeepsGeneralElementType() {
    var registry = registry(List.of(first, get, count, second, find));
    var types = TypeSet.of(element);

    var result = MetadataCollectionSpecializer.buildPerOwnerCollectionMembers(
      registry, base, element, List.of(), "Владелец");

    assertThat(result).containsExactly(
      MetadataCollectionSpecializer.withElementReturnType(get, types), count,
      MetadataCollectionSpecializer.withElementReturnType(find, types));
  }

  private TypeRegistry registry(List<MemberDescriptor> members) {
    var registry = mock(TypeRegistry.class);
    when(registry.getMembers(base, FileType.BSL)).thenReturn(members);
    when(registry.intern(eq(TypeKind.PLATFORM), anyString()))
      .thenAnswer(invocation -> new TypeRef(TypeKind.PLATFORM, invocation.getArgument(1)));
    return registry;
  }

  private static MD namedChild(String name) {
    var child = mock(MD.class);
    when(child.getName()).thenReturn(name);
    return child;
  }

  private static MemberDescriptor materialized(MemberDescriptor template, String name, String type) {
    return MetadataCollectionSpecializer.materializeChildMember(template, BilingualString.of(name),
      TypeSet.of(new TypeRef(TypeKind.PLATFORM, type)));
  }
}
