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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalNameIndexReuseTest {
  private final AtomicReference<List<MemberDescriptor>> bsl = new AtomicReference<>(List.of());
  private final AtomicReference<List<MemberDescriptor>> os = new AtomicReference<>(List.of());
  private GlobalScopeProvider globals;

  @BeforeEach
  void createGlobals() {
    var registry = mock(TypeRegistry.class);
    when(registry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.BSL)).thenAnswer(call -> bsl.get());
    when(registry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.OS)).thenAnswer(call -> os.get());
    globals = new GlobalScopeProvider(mock(BslContextHolder.class), registry);
  }

  @Test
  void unchangedNamesUseFreshTypesSymbolsAndMemberKinds() {
    var member = member("Свойство", "Property");
    bsl.set(List.of(member));
    assertThat(globals.globalProperty("PROPERTY", FileType.BSL)).containsSame(member);
    var before = names(FileType.BSL);
    var symbol = mock(SourceDefinedSymbol.class);
    var current = member.withReturnTypes(TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "String")))
      .withSourceSymbol(symbol);
    bsl.set(List.of(current));
    assertThat(globals.globalProperty("свойство", FileType.BSL)).containsSame(current);
    assertThat(names(FileType.BSL)).isSameAs(before);
    bsl.set(List.of(MemberDescriptor.method("Свойство").withBilingualName(member.bilingualName())));
    assertThat(globals.globalProperty("Property", FileType.BSL)).isEmpty();
    assertThat(globals.globalFunction("Property", FileType.BSL)).isPresent();
    assertThat(names(FileType.BSL)).isSameAs(before);
  }

  @Test
  void reorderingNamesRebuildsPositions() {
    var first=member("Первое", "First");
    var second=member("Второе", "Second");
    bsl.set(List.of(first,second));
    assertThat(globals.globalProperty("First",FileType.BSL)).containsSame(first);
    var before=names(FileType.BSL);
    bsl.set(List.of(second,first));
    assertThat(globals.globalProperty("FIRST",FileType.BSL)).containsSame(first);
    assertThat(names(FileType.BSL)).isNotSameAs(before);
  }

  @Test
  void changingOnlyAnEnglishAliasRemovesTheOldName() {
    bsl.set(List.of(member("Имя","Old")));
    assertThat(globals.globalProperty("old",FileType.BSL)).isPresent();
    var current=member("Имя","New");
    bsl.set(List.of(current));
    assertThat(globals.globalProperty("old",FileType.BSL)).isEmpty();
    assertThat(globals.globalProperty("new",FileType.BSL)).containsSame(current);
  }

  @Test
  void additionsRemovalsAndEmptySourcesCannotKeepOldPositions() {
    var first=member("A","AliasA");
    var second=member("B","AliasB");
    bsl.set(List.of(first));
    assertThat(globals.globalProperty("AliasA",FileType.BSL)).containsSame(first);
    bsl.set(List.of(first,second));
    assertThat(globals.globalProperty("AliasB",FileType.BSL)).containsSame(second);
    bsl.set(List.of(second));
    assertThat(globals.globalProperty("AliasA",FileType.BSL)).isEmpty();
    assertThat(globals.globalProperty("AliasB",FileType.BSL)).containsSame(second);
    bsl.set(List.of());
    assertThat(globals.globalProperty("AliasB",FileType.BSL)).isEmpty();
  }

  @Test
  void bilingualCollisionsKeepFirstPositionAfterDescriptorRefresh() {
    var first=member("Первое","Same");
    var second=member("Same","Second");
    bsl.set(List.of(first,second));
    assertThat(globals.globalProperty("same",FileType.BSL)).containsSame(first);
    var current=first.withReturnTypes(TypeSet.of(new TypeRef(TypeKind.PRIMITIVE,"Number")));
    bsl.set(List.of(current,second));
    assertThat(globals.globalProperty("SAME",FileType.BSL)).containsSame(current);
    assertThat(globals.globalProperty("Second",FileType.BSL)).containsSame(second);
  }

  @Test
  void languagesReuseOnlyTheirOwnMappingWhenTheOtherSourceChanges() {
    var common=member("Общее","Common");
    bsl.set(List.of(common));os.set(List.of(common));
    assertThat(globals.globalProperty("Common",FileType.BSL)).containsSame(common);
    var beforeBsl=names(FileType.BSL);
    os.set(List.of(member("Другое","Other")));
    assertThat(globals.globalProperty("Common",FileType.OS)).isEmpty();
    assertThat(globals.globalProperty("Other",FileType.OS)).isPresent();
    assertThat(names(FileType.BSL)).isSameAs(beforeBsl);
    assertThat(globals.globalProperty("Common",FileType.BSL)).containsSame(common);
  }

  private static MemberDescriptor member(String ru,String en) {
    return MemberDescriptor.property(ru,new TypeRef(TypeKind.PRIMITIVE,"Number"),"")
      .withBilingualName(new BilingualString(ru,en));
  }

  @SuppressWarnings("unchecked")
  private Map<String,Integer> names(FileType fileType) {
    AtomicReference<?> ref=(AtomicReference<?>)ReflectionTestUtils.getField(globals,"globalIndexRef");
    Map<FileType,Map<String,Integer>> maps=ReflectionTestUtils.invokeMethod(ref.get(),"byName");
    return maps.get(fileType);
  }
}
