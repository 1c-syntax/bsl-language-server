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
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdo.CommonTemplate;
import com.github._1c_syntax.bsl.mdo.MDObject;
import com.github._1c_syntax.bsl.mdo.Style;
import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link ConfigurationTypesProvider#registerCommonLibraryExpansion}
 * как чистого helper'а: без mdclasses-источников проверяем все ветки
 * (no-op-условия + позитивный сценарий с одним placeholder'ом).
 */
class RegisterCommonLibraryExpansionTest {

  private final TypeRegistry registry = new TypeRegistry(
    List.of(),
    Mockito.mock(GlobalScopeProvider.class),
    Mockito.mock(MemberMetadataIndex.class)
  );

  /**
   * Регистрирует у типа generic-template'мер вида {@code <placeholderName>}.
   * Тип попадает в реестр через {@link TypeRegistry#registerConfigurationType}
   * — это самый дешёвый путь обеспечить, что {@code resolve(name)} находит
   * TypeRef в unit-тесте без поднятия Spring-контекста.
   */
  private void seedGenericTemplate(String typeName, String placeholderName,
                                   String enPlaceholder, TypeRef valueRef) {
    var ref = registry.registerConfigurationType(typeName);
    var template = MemberDescriptor.genericProperty(
        "<" + placeholderName + ">", valueRef, "")
      .withBilingualName(BilingualString.of(
        "<" + placeholderName + ">", "<" + enPlaceholder + ">"));
    registry.registerMemberSource(ref, () -> List.of(template), FileType.BSL);
  }

  @Test
  void materializesMembers_fromChildNames() {
    var pictureRef = registry.intern(TypeKind.PLATFORM, "Картинка");
    seedGenericTemplate("БиблиотекаКартинок", "Имя картинки", "Icon name", pictureRef);

    ConfigurationGenericExpander.registerCommonLibraryExpansion(
      registry, "БиблиотекаКартинок", List.of("ОбщаяКартинка1", "ОбщаяКартинка2"));

    var libRef = registry.resolve("БиблиотекаКартинок").orElseThrow();
    var memberNames = registry.getMembers(libRef, FileType.BSL).stream()
      .map(MemberDescriptor::name)
      .toList();
    // Шаблон сохраняется (он же source); добавились материализованные имена.
    assertThat(memberNames)
      .containsExactlyInAnyOrder("ОбщаяКартинка1", "ОбщаяКартинка2", "<Имя картинки>");
  }

  @Test
  void materializedMembers_returnTypeFollowsTemplate() {
    var pictureRef = registry.intern(TypeKind.PLATFORM, "Картинка");
    seedGenericTemplate("БиблиотекаКартинок", "Имя картинки", "Icon name", pictureRef);

    ConfigurationGenericExpander.registerCommonLibraryExpansion(
      registry, "БиблиотекаКартинок", List.of("MyPic"));

    var libRef = registry.resolve("БиблиотекаКартинок").orElseThrow();
    var materialized = registry.getMembers(libRef, FileType.BSL).stream()
      .filter(m -> m.name().equals("MyPic"))
      .findFirst()
      .orElseThrow();
    assertThat(materialized.returnType().qualifiedName()).isEqualTo("Картинка");
    assertThat(materialized.generic()).isFalse();
  }

  @Test
  void emptyChildNames_isNoOp() {
    seedGenericTemplate("БиблиотекаСтилей", "Имя стиля", "Style name",
      registry.intern(TypeKind.PLATFORM, "Стиль"));

    ConfigurationGenericExpander.registerCommonLibraryExpansion(
      registry, "БиблиотекаСтилей", List.of());

    var libRef = registry.resolve("БиблиотекаСтилей").orElseThrow();
    var memberNames = registry.getMembers(libRef, FileType.BSL).stream()
      .map(MemberDescriptor::name)
      .toList();
    assertThat(memberNames).containsExactly("<Имя стиля>");
  }

  @Test
  void typeMissingFromRegistry_isNoOp() {
    // ни до seed, ни после — типа нет.
    ConfigurationGenericExpander.registerCommonLibraryExpansion(
      registry, "НеСуществующаяБиблиотека", List.of("X", "Y"));
    assertThat(registry.resolve("НеСуществующаяБиблиотека")).isEmpty();
  }

  @Test
  void appearanceTemplatesOf_keepsOnlyDataCompositionAppearanceType() {
    // Общие макеты бывают разных типов; в БиблиотекаМакетовОформленияКомпоновкиДанных
    // относятся только TemplateType.DATA_COMPOSITION_APPEARANCE_TEMPLATE.
    var dcsl = CommonTemplate.builder()
      .name("Светлый")
      .templateType(TemplateType.DATA_COMPOSITION_APPEARANCE_TEMPLATE)
      .build();
    var spreadsheet = CommonTemplate.builder()
      .name("Печатный")
      .templateType(TemplateType.SPREADSHEET_DOCUMENT)
      .build();
    var cfg = Mockito.mock(CF.class);
    Mockito.when(cfg.getCommonTemplates()).thenReturn(List.of(dcsl, spreadsheet));

    var filtered = ConfigurationGenericExpander.appearanceTemplatesOf(cfg);
    assertThat(filtered).extracting(MDObject::getName).containsExactly("Светлый");
  }

  @Test
  void namesOf_skipsBlankNames() {
    var withName = Style.builder().name("Темный").build();
    var withBlank = Style.builder().name("").build();
    assertThat(ConfigurationGenericExpander.namesOf(List.of(withName, withBlank)))
      .containsExactly("Темный");
  }

  @Test
  void namesOf_emptyList_returnsEmpty() {
    assertThat(ConfigurationGenericExpander.namesOf(List.<Style>of()))
      .isEmpty();
  }

  @Test
  void genericTemplateWithoutPlaceholderInName_snapshotEmpty_isNoOp() {
    // Generic-member есть, но в его имени НЕТ placeholder'а в брэкетах —
    // expandTemplate выходит на ruMatch == null, snapshot пустой.
    var libRef = registry.intern(TypeKind.PLATFORM, "БиблиотекаБезPlaceholder");
    var generic = MemberDescriptor.genericProperty("ПростоеИмя",
        registry.intern(TypeKind.PLATFORM, "Строка"), "");
    registry.registerMemberSource(libRef, () -> List.of(generic), FileType.BSL);

    ConfigurationGenericExpander.registerCommonLibraryExpansion(
      registry, "БиблиотекаБезPlaceholder", List.of("X"));

    assertThat(registry.getMembers(libRef, FileType.BSL))
      .extracting(MemberDescriptor::name)
      .containsExactly("ПростоеИмя");
  }

  @Test
  void typeWithoutGenericTemplate_isNoOp() {
    // Тип в реестре зарегистрирован, но среди членов нет generic-template'а —
    // expansion не находит placeholder, ничего не добавляет.
    var libRef = registry.intern(TypeKind.PLATFORM, "БиблиотекаБезШаблона");
    var plain = MemberDescriptor.property("Реальный",
      registry.intern(TypeKind.PLATFORM, "Строка"));
    registry.registerMemberSource(libRef, () -> List.of(plain), FileType.BSL);

    ConfigurationGenericExpander.registerCommonLibraryExpansion(
      registry, "БиблиотекаБезШаблона", List.of("X"));

    assertThat(registry.getMembers(libRef, FileType.BSL))
      .extracting(MemberDescriptor::name)
      .containsExactly("Реальный");
  }
}
