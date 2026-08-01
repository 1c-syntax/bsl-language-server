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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.CommentTypeResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.TypeRelations;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.OScriptExtends;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OScriptModuleMembersProviderTest {

  @Mock
  private TypeRegistry typeRegistry;
  @Mock
  private OScriptLibraryIndex oScriptLibraryIndex;
  @Mock
  private GlobalScopeProvider globalScopeProvider;
  @Mock
  private OScriptExtends oScriptExtends;
  @Mock
  private TypeRelations typeRelations;
  @Mock
  private OScriptIterable oScriptIterable;
  @Mock
  private CommentTypeResolver commentTypeResolver;

  private OScriptModuleMembersProvider provider;

  @BeforeEach
  void setUp() {
    provider = new OScriptModuleMembersProvider(
      typeRegistry, oScriptLibraryIndex, globalScopeProvider, oScriptExtends, typeRelations, oScriptIterable,
      commentTypeResolver);
  }

  @Test
  void dualRoleFileIndexesModuleTypeNotClassType() {
    // given — один .os-файл, объявленный в lib.config и как модуль, и как класс.
    var uri = URI.create("file:///lib/dual.os");
    var moduleEntry = new LibraryEntry(uri, "dual.Модуль", EntryKind.MODULE, "lib", false);
    var classEntry = new LibraryEntry(uri, "dual.Класс", EntryKind.CLASS, "lib", false);
    // MODULE регистрируется раньше CLASS — проверяем, что CLASS не перетрёт обратный индекс.
    when(oScriptLibraryIndex.findEntriesByUri(uri)).thenReturn(List.of(moduleEntry, classEntry));

    var documentContext = mock(DocumentContext.class);
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    when(documentContext.getUri()).thenReturn(uri);
    var symbolTree = mock(SymbolTree.class);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getModule()).thenReturn(mock(ModuleSymbol.class));

    var moduleRef = new TypeRef(TypeKind.USER, "dual.Модуль");
    var classRef = new TypeRef(TypeKind.USER, "dual.Класс");
    when(typeRegistry.registerUserType(eq("dual.Модуль"), any(), eq(FileType.OS))).thenReturn(moduleRef);
    when(typeRegistry.registerUserType(eq("dual.Класс"), any(), eq(FileType.OS))).thenReturn(classRef);

    // Мок self-типа хранит состояние как реальный GlobalScopeProvider (Map<URI, TypeRef>):
    // регистрация класса читает то, что перед этим записала регистрация модуля того же URI.
    var selfTypeByUri = new HashMap<URI, TypeRef>();
    when(globalScopeProvider.moduleTypeRefByUri(any()))
      .thenAnswer(invocation -> Optional.ofNullable(selfTypeByUri.get(invocation.getArgument(0))));
    doAnswer(invocation -> selfTypeByUri.put(invocation.getArgument(0), invocation.getArgument(1)))
      .when(globalScopeProvider).indexModuleType(any(), any());

    // when
    provider.register(documentContext);

    // then — обратный индекс URI→тип хранит тип модуля, а не класса.
    assertThat(selfTypeByUri).containsEntry(uri, moduleRef);
    verify(globalScopeProvider, never()).indexModuleType(uri, classRef);
  }

  @Test
  void iterableAnnotationMarksUserTypeAsForEachCollection() {
    // given — небиблиотечный .os, помеченный &Обходимое.
    var uri = URI.create("file:///КоллекцияЧисел.os");
    when(oScriptLibraryIndex.findEntriesByUri(uri)).thenReturn(List.of());

    var documentContext = mock(DocumentContext.class);
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    when(documentContext.getUri()).thenReturn(uri);
    var symbolTree = mock(SymbolTree.class);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getModule()).thenReturn(mock(ModuleSymbol.class));

    var ref = new TypeRef(TypeKind.USER, "КоллекцияЧисел");
    when(typeRegistry.registerUserType(eq("КоллекцияЧисел"), any(), eq(FileType.OS))).thenReturn(ref);
    when(oScriptIterable.isIterable(documentContext)).thenReturn(true);

    // when
    provider.register(documentContext);

    // then — тип помечен обходимой коллекцией.
    verify(typeRegistry).setUserTypeIterable(ref, true, FileType.OS);
  }

  @Test
  void plainClassIsNotMarkedAsForEachCollection() {
    // given — небиблиотечный .os без &Обходимое.
    var uri = URI.create("file:///ОбычныйКласс.os");
    when(oScriptLibraryIndex.findEntriesByUri(uri)).thenReturn(List.of());

    var documentContext = mock(DocumentContext.class);
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    when(documentContext.getUri()).thenReturn(uri);
    var symbolTree = mock(SymbolTree.class);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getModule()).thenReturn(mock(ModuleSymbol.class));

    var ref = new TypeRef(TypeKind.USER, "ОбычныйКласс");
    when(typeRegistry.registerUserType(eq("ОбычныйКласс"), any(), eq(FileType.OS))).thenReturn(ref);
    when(oScriptIterable.isIterable(documentContext)).thenReturn(false);

    // when
    provider.register(documentContext);

    // then — признак коллекции снимается (false), а не остаётся от прежнего состояния.
    verify(typeRegistry).setUserTypeIterable(ref, false, FileType.OS);
  }

  @Test
  void oscriptClassGetsBuiltinRaiseEventMember() {
    // given — небиблиотечный .os-класс.
    var uri = URI.create("file:///КлассССобытием.os");
    when(oScriptLibraryIndex.findEntriesByUri(uri)).thenReturn(List.of());

    var documentContext = mock(DocumentContext.class);
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    when(documentContext.getUri()).thenReturn(uri);
    when(documentContext.getModuleType()).thenReturn(ModuleType.OScriptClass);
    var symbolTree = mock(SymbolTree.class);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getModule()).thenReturn(mock(ModuleSymbol.class));

    var ref = new TypeRef(TypeKind.USER, "КлассССобытием");
    when(typeRegistry.registerUserType(eq("КлассССобытием"), any(), eq(FileType.OS))).thenReturn(ref);

    var sourceCaptor = ArgumentCaptor.forClass(MemberSource.class);

    // when
    provider.register(documentContext);

    // then — среди зарегистрированных источников членов есть встроенный ВызватьСобытие/RaiseEvent,
    // доступный у ЛЮБОГО OScript-класса вне зависимости от объявленных им самим членов.
    verify(typeRegistry, atLeastOnce()).registerMemberSource(eq(ref), sourceCaptor.capture(), eq(FileType.OS));
    var allMembers = sourceCaptor.getAllValues().stream()
      .flatMap(source -> source.getMembers().stream())
      .toList();
    var raiseEvent = allMembers.stream()
      .filter(m -> "ВызватьСобытие".equals(m.name()))
      .findFirst()
      .orElseThrow(() -> new AssertionError("ВызватьСобытие должен быть встроенным членом OScript-класса"));

    assertThat(raiseEvent.bilingualName().en()).isEqualTo("RaiseEvent");
    assertThat(raiseEvent.signatures()).hasSize(1);
    assertThat(raiseEvent.signatures().getFirst().parameters()).hasSize(2);
  }

  @Test
  void oscriptClassSelfTypeIsIndexedForUnqualifiedAccessInsideItsOwnBody() {
    // given — небиблиотечный .os-класс (не объявлен ни в каком lib.config).
    var uri = URI.create("file:///КлассБезБиблиотеки.os");
    when(oScriptLibraryIndex.findEntriesByUri(uri)).thenReturn(List.of());

    var documentContext = mock(DocumentContext.class);
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    when(documentContext.getUri()).thenReturn(uri);
    when(documentContext.getModuleType()).thenReturn(ModuleType.OScriptClass);
    var symbolTree = mock(SymbolTree.class);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getModule()).thenReturn(mock(ModuleSymbol.class));

    var ref = new TypeRef(TypeKind.USER, "КлассБезБиблиотеки");
    when(typeRegistry.registerUserType(eq("КлассБезБиблиотеки"), any(), eq(FileType.OS))).thenReturn(ref);

    // when
    provider.register(documentContext);

    // then — URI класса связан с его же типом: комплишен/инференсер внутри тела
    // класса могут найти его члены (включая встроенные) как self-члены.
    verify(globalScopeProvider).indexModuleType(uri, ref);
  }
}
