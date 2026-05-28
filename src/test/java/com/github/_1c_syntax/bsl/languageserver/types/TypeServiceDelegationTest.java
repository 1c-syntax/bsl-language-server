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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты на делегирующие методы {@link TypeService} (getDescription,
 * getConstructors, resolve, getMembers, findGlobalContextNames). Сам
 * TypeRegistry мокается — проверяется только пробрасывание аргументов.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TypeServiceDelegationTest {

  private static final TypeRef ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");

  @Mock
  private TypeRegistry typeRegistry;
  @Mock
  private SymbolTypeIndex symbolTypeIndex;
  @Mock
  private ExpressionTypeInferencer inferencer;
  @Mock
  private ReferenceResolver referenceResolver;
  @Mock
  private GlobalScopeProvider globalScopeProvider;

  private TypeService typeService;

  @BeforeEach
  void setUp() {
    typeService = new TypeService(typeRegistry, symbolTypeIndex, inferencer,
      referenceResolver, globalScopeProvider);
  }

  @Test
  void getDescriptionDelegatesToRegistry() {
    // given
    when(typeRegistry.getDescription(ARRAY)).thenReturn("описание");

    // when / then
    assertThat(typeService.getDescription(ARRAY)).isEqualTo("описание");
  }

  @Test
  void getConstructorsDelegatesToRegistry() {
    // given
    var sig = SignatureDescriptor.EMPTY;
    when(typeRegistry.getConstructors(ARRAY)).thenReturn(List.of(sig));

    // when / then
    assertThat(typeService.getConstructors(ARRAY)).containsExactly(sig);
  }

  @Test
  void resolveDelegatesToRegistry() {
    // given
    when(typeRegistry.resolve("Массив")).thenReturn(Optional.of(ARRAY));

    // when / then
    assertThat(typeService.resolve("Массив")).contains(ARRAY);
  }

  @Test
  void getGlobalContextNamesDelegatesAndBootstrapsTypeRegistry() {
    // given
    when(globalScopeProvider.getGlobalContextNames()).thenReturn(List.of("X", "Y"));

    // when
    var names = typeService.getGlobalContextNames();

    // then
    assertThat(names).containsExactly("X", "Y");
    // bootstrap триггер: typeService.resolve("") вызван
    verify(typeRegistry).resolve("");
  }

  @Test
  void getGlobalContextNamesByFileTypeDelegates() {
    // given
    when(globalScopeProvider.getGlobalContextNames(FileType.BSL)).thenReturn(List.of("Z"));

    // when
    var names = typeService.getGlobalContextNames(FileType.BSL);

    // then
    assertThat(names).containsExactly("Z");
    verify(typeRegistry).resolve("");
  }

  @Test
  void findGlobalContextDelegates() {
    // given
    when(globalScopeProvider.findGlobalContext("X", FileType.BSL))
      .thenReturn(Optional.of(ARRAY));
    lenient().when(typeRegistry.resolve("X")).thenReturn(Optional.empty());

    // when / then
    assertThat(typeService.findGlobalContext("X", FileType.BSL)).contains(ARRAY);
  }

  @Test
  void findTypesByReferenceForSyntheticSymbolUsesValueType() {
    // given — Reference с SyntheticSymbol, у которого valueType=NUMBER.
    var synthetic = new SyntheticSymbol("X", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "", NUMBER);
    var location = new Location("file:///fake.bsl", new Range());
    var ref = Reference.of(null, synthetic, location, OccurrenceType.REFERENCE);

    // when
    var types = typeService.findTypes(ref);

    // then
    assertThat(types.refs()).containsExactly(NUMBER);
  }

  @Test
  void findTypesByReferenceWithUnknownSyntheticValueReturnsEmpty() {
    // given
    var synthetic = new SyntheticSymbol("X", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");
    var location = new Location("file:///fake.bsl", new Range());
    var ref = Reference.of(null, synthetic, location, OccurrenceType.REFERENCE);

    // when
    var types = typeService.findTypes(ref);

    // then — нет valueType → EMPTY.
    assertThat(types).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void getDeclaredReturnTypesDelegatesToSymbolTypeIndex() {
    // given
    var method = org.mockito.Mockito.mock(
      com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol.class);
    when(symbolTypeIndex.getDeclaredReturnTypes(method)).thenReturn(TypeSet.of(NUMBER));

    // when / then
    assertThat(typeService.getDeclaredReturnTypes(method).refs()).containsExactly(NUMBER);
  }

  @Test
  void getMembersDelegatesToRegistry() {
    // given
    var m = MemberDescriptor.method("F");
    when(typeRegistry.getMembers(ARRAY)).thenReturn(List.of(m));

    // when / then
    assertThat(typeService.getMembers(ARRAY)).containsExactly(m);
  }

  @Test
  void getMembersByFileTypeDelegates() {
    // given
    var m = MemberDescriptor.method("F");
    when(typeRegistry.getMembers(eq(ARRAY), any(FileType.class))).thenReturn(List.of(m));

    // when / then
    assertThat(typeService.getMembers(ARRAY, FileType.BSL)).containsExactly(m);
  }

  @Test
  void resolveByFileTypeDelegates() {
    // given
    when(typeRegistry.resolve(eq("Массив"), any(FileType.class)))
      .thenReturn(Optional.of(ARRAY));

    // when / then
    assertThat(typeService.resolve("Массив", FileType.BSL)).contains(ARRAY);
  }

  @Test
  void getDescriptionByLanguageDelegates() {
    // given
    when(typeRegistry.getDescription(eq(ARRAY),
      any(com.github._1c_syntax.bsl.languageserver.configuration.Language.class)))
      .thenReturn("ru-описание");

    // when / then
    assertThat(typeService.getDescription(ARRAY,
      com.github._1c_syntax.bsl.languageserver.configuration.Language.RU))
      .isEqualTo("ru-описание");
  }

  @Test
  void getDescriptionByFileTypeDelegates() {
    // given
    when(typeRegistry.getDescription(eq(ARRAY), any(FileType.class)))
      .thenReturn("bsl-описание");

    // when / then
    assertThat(typeService.getDescription(ARRAY, FileType.BSL))
      .isEqualTo("bsl-описание");
  }

  @Test
  void getConstructorsByFileTypeDelegates() {
    // given
    var sig = SignatureDescriptor.EMPTY;
    when(typeRegistry.getConstructors(eq(ARRAY), any(FileType.class)))
      .thenReturn(List.of(sig));

    // when / then
    assertThat(typeService.getConstructors(ARRAY, FileType.BSL)).containsExactly(sig);
  }

  @Test
  void findGlobalContextNoArgUsesNullFileType() {
    // given
    when(globalScopeProvider.findGlobalContext(eq("X"), eq((FileType) null)))
      .thenReturn(Optional.of(ARRAY));
    lenient().when(typeRegistry.resolve("X")).thenReturn(Optional.empty());

    // when / then
    assertThat(typeService.findGlobalContext("X")).contains(ARRAY);
  }
}
