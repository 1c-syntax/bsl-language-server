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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Тип имени, разрешаемого не в теле метода, а в модуле и глобальной области.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScopeMemberTypeResolverTest {

  private static final TypeRef MODULE_REF = new TypeRef(TypeKind.CONFIGURATION, "ОбщийМодуль.ОбщегоНазначения");
  private static final TypeRef STRING_REF = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Mock
  private TypeRegistry typeRegistry;

  @Mock
  private GlobalScopeProvider globalScopeProvider;

  @Mock
  private DocumentContext documentContext;

  @Mock
  private ModuleSymbol module;

  @InjectMocks
  private ScopeMemberTypeResolver resolver;

  @Test
  void moduleNameIsTypedAsItsModuleType() {
    // given
    givenDocument();
    when(module.getOwner()).thenReturn(documentContext);
    when(globalScopeProvider.moduleTypeRefByUri(documentContext.getUri())).thenReturn(Optional.of(MODULE_REF));

    // when
    var types = resolver.moduleType(module);

    // then
    assertThat(types.refs()).containsExactly(MODULE_REF);
  }

  @Test
  void moduleWithoutRegisteredTypeGivesNothing() {
    // given
    givenDocument();
    when(module.getOwner()).thenReturn(documentContext);
    when(globalScopeProvider.moduleTypeRefByUri(documentContext.getUri())).thenReturn(Optional.empty());

    // when
    var types = resolver.moduleType(module);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void selfMemberTypeIsTakenFromModuleOwnType() {
    // given
    givenDocument();
    when(globalScopeProvider.moduleTypeRefByUri(documentContext.getUri())).thenReturn(Optional.of(MODULE_REF));
    when(typeRegistry.findMember(MODULE_REF, MemberKind.PROPERTY, "Наименование", FileType.BSL))
      .thenReturn(Optional.of(member(TypeSet.of(STRING_REF))));

    // when
    var types = resolver.selfMemberType(documentContext, "Наименование", MemberKind.PROPERTY);

    // then
    assertThat(types).contains(TypeSet.of(STRING_REF));
  }

  @Test
  void selfMemberTypeIsEmptyWhenModuleHasNoOwnType() {
    // given
    givenDocument();
    when(globalScopeProvider.moduleTypeRefByUri(documentContext.getUri())).thenReturn(Optional.empty());

    // when
    var types = resolver.selfMemberType(documentContext, "Наименование", MemberKind.PROPERTY);

    // then
    assertThat(types).isEmpty();
  }

  @Test
  void globalPropertyTypeIsTaken() {
    // given
    givenDocument();
    when(globalScopeProvider.globalProperty("ТекущаяДата", FileType.BSL))
      .thenReturn(Optional.of(member(TypeSet.of(STRING_REF))));

    // when
    var types = resolver.globalPropertyType(documentContext, "ТекущаяДата");

    // then
    assertThat(types.refs()).containsExactly(STRING_REF);
  }

  @Test
  void globalPropertyOfUnknownTypeGivesNothing() {
    // given: свойство есть, но тип его значения неизвестен — подсказывать нечего.
    givenDocument();
    when(globalScopeProvider.globalProperty("Загадка", FileType.BSL))
      .thenReturn(Optional.of(member(TypeSet.of(TypeRef.UNKNOWN))));

    // when
    var types = resolver.globalPropertyType(documentContext, "Загадка");

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void globalFunctionReturnTypeIsTaken() {
    // given
    givenDocument();
    when(globalScopeProvider.globalFunction("СтрНайти", FileType.BSL))
      .thenReturn(Optional.of(member(TypeSet.of(STRING_REF))));

    // when
    var types = resolver.globalFunctionType(documentContext, "СтрНайти");

    // then
    assertThat(types.refs()).containsExactly(STRING_REF);
  }

  @Test
  void blankFunctionNameGivesNothing() {
    // given / when
    var types = resolver.globalFunctionType(documentContext, "  ");

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  private void givenDocument() {
    when(documentContext.getUri()).thenReturn(Absolute.uri("file:///module.bsl"));
    when(documentContext.getFileType()).thenReturn(FileType.BSL);
  }

  private static MemberDescriptor member(TypeSet returnTypes) {
    return new MemberDescriptor(
      new BilingualString("Имя", "Name"),
      MemberKind.PROPERTY,
      BilingualString.EMPTY,
      returnTypes,
      List.of(),
      null,
      false,
      PlatformMetadata.EMPTY,
      false,
      false
    );
  }
}
