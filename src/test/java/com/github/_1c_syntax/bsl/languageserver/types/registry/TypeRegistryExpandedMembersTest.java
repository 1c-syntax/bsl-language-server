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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link TypeRegistry#expandedMembers}: snapshot-материализация
 * generic-template'ов без регистрации source. Нужен для случаев self-target
 * expansion (specializedRef == genericRef), где обычный
 * {@code registerMemberExpansion} даёт бесконечную рекурсию через {@code getMembers}.
 */
class TypeRegistryExpandedMembersTest {

  private final TypeRegistry registry = new TypeRegistry(
    List.of(),
    Mockito.mock(MemberMetadataIndex.class)
  );

  @Test
  void expandsSinglePlaceholderTemplate() {
    var ref = registry.intern(TypeKind.PLATFORM, "БиблиотекаКартинок");
    var pictureRef = registry.intern(TypeKind.PLATFORM, "Картинка");
    // Регистрируем у типа generic-template'мер <Имя картинки>, как это сделал бы
    // bsl-context-провайдер для enum-«библиотеки».
    var template = MemberDescriptor.genericProperty("<Имя картинки>", pictureRef, "")
      .withBilingualName(BilingualString.of("<Имя картинки>", "<Icon name>"));
    MemberSource src = () -> List.of(template);
    registry.registerMemberSource(ref, src, FileType.BSL);

    var expanded = registry.expandedMembers(ref, Map.of(),
      Map.of("Имя картинки", List.of("ОбщаяКартинка1", "ОбщаяКартинка2")), FileType.BSL);

    assertThat(expanded)
      .extracting(MemberDescriptor::name)
      .containsExactly("ОбщаяКартинка1", "ОбщаяКартинка2");
    assertThat(expanded).allMatch(m -> !m.generic());
  }

  @Test
  void expandsBilingualName_replacesPlaceholderInBothSides() {
    var ref = registry.intern(TypeKind.PLATFORM, "БиблиотекаКартинок");
    var pictureRef = registry.intern(TypeKind.PLATFORM, "Картинка");
    var template = MemberDescriptor.genericProperty("<Имя картинки>", pictureRef, "")
      .withBilingualName(BilingualString.of("<Имя картинки>", "<Icon name>"));
    registry.registerMemberSource(ref, () -> List.of(template), FileType.BSL);

    var expanded = registry.expandedMembers(ref, Map.of(),
      Map.of("Имя картинки", List.of("MyIcon")), FileType.BSL);

    assertThat(expanded).hasSize(1);
    var member = expanded.get(0);
    assertThat(member.bilingualName().primary()).isEqualTo("MyIcon");
    assertThat(member.bilingualName().en()).isEqualTo("MyIcon");
  }

  @Test
  void emptyExpansions_returnsEmpty() {
    var ref = registry.intern(TypeKind.PLATFORM, "Тип");
    var expanded = registry.expandedMembers(ref, Map.of(), Map.of(), FileType.BSL);
    assertThat(expanded).isEmpty();
  }

  @Test
  void noGenericTemplates_returnsEmpty() {
    var ref = registry.intern(TypeKind.PLATFORM, "Тип");
    var plainMember = MemberDescriptor.property("Регулярный",
      registry.intern(TypeKind.PLATFORM, "Строка"));
    registry.registerMemberSource(ref, () -> List.of(plainMember), FileType.BSL);

    var expanded = registry.expandedMembers(ref, Map.of(),
      Map.of("Имя", List.of("X")), FileType.BSL);
    assertThat(expanded).isEmpty();
  }

  @Test
  void unrelatedPlaceholderKey_returnsEmpty() {
    var ref = registry.intern(TypeKind.PLATFORM, "Тип");
    var template = MemberDescriptor.genericProperty("<Имя картинки>",
      registry.intern(TypeKind.PLATFORM, "Картинка"), "");
    registry.registerMemberSource(ref, () -> List.of(template), FileType.BSL);

    // У шаблона placeholder «Имя картинки», но в expansions передаём «Имя стиля» —
    // нет совпадения, expansion не материализует ничего.
    var expanded = registry.expandedMembers(ref, Map.of(),
      Map.of("Имя стиля", List.of("X")), FileType.BSL);
    assertThat(expanded).isEmpty();
  }

  @Test
  void registerMemberExpansion_emptyMap_isNoOp() {
    var ref = registry.intern(TypeKind.PLATFORM, "T");
    var gen = registry.intern(TypeKind.PLATFORM, "T<gen>");
    registry.registerMemberExpansion(ref, gen, Map.of(), Map.of(), FileType.BSL);
    assertThat(registry.getMembers(ref, FileType.BSL)).isEmpty();
  }

  @Test
  void registerMemberExpansion_materializesAndIndexes() {
    var specRef = registry.intern(TypeKind.PLATFORM, "ПеречислениеМенеджер.ВидыКонтрагента");
    var genRef = registry.intern(TypeKind.PLATFORM, "ПеречислениеМенеджер.<Имя перечисления>");
    var pictureRef = registry.intern(TypeKind.PLATFORM, "ПеречислениеСсылка.ВидыКонтрагента");
    var template = MemberDescriptor.genericProperty("<Имя значения>", pictureRef, "")
      .withBilingualName(BilingualString.of("<Имя значения>", "<Value name>"));
    registry.registerMemberSource(genRef, () -> List.of(template), FileType.BSL);

    registry.registerMemberExpansion(specRef, genRef,
      Map.of("Имя перечисления", "ВидыКонтрагента"),
      Map.of("Имя значения", List.of("Юридическое", "Физическое")),
      FileType.BSL);

    var members = registry.getMembers(specRef, FileType.BSL);
    assertThat(members).extracting(MemberDescriptor::name)
      .containsExactly("Юридическое", "Физическое");
  }

  @Test
  void emptyTypeBindings_doesNotFail() {
    var ref = registry.intern(TypeKind.PLATFORM, "Тип");
    var template = MemberDescriptor.genericProperty("<X>", ref, "")
      .withBilingualName(BilingualString.of("<X>", "<X>"));
    registry.registerMemberSource(ref, () -> List.of(template), FileType.BSL);
    assertThat(registry.expandedMembers(ref, Map.of(), Map.of("X", List.of("Один")), FileType.BSL))
      .extracting(MemberDescriptor::name)
      .containsExactly("Один");
  }
}
