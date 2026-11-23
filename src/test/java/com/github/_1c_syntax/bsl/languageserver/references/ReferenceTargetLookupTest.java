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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.references.model.LocationRepository;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.references.model.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrence;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrenceRepository;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.GenericInterner;
import com.github._1c_syntax.utils.StringInterner;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferenceTargetLookupTest {
  private final URI uri = URI.create("file:///target-lookup.bsl");
  private final Position position = new Position(3, 2);
  private final ServerContextProvider provider = mock(ServerContextProvider.class);
  private final ServerContext server = mock(ServerContext.class);
  private final DocumentContext document = mock(DocumentContext.class);
  private final DocumentContext foreign = mock(DocumentContext.class);
  private final SymbolTree tree = mock(SymbolTree.class);
  private final SymbolTree foreignTree = mock(SymbolTree.class);
  private final LocationRepository locations = mock(LocationRepository.class);
  private final SelfMemberResolver self = mock(SelfMemberResolver.class);
  private final ReferenceIndex index = new ReferenceIndex(provider, mock(StringInterner.class), new GenericInterner<>(),
    locations, mock(SymbolOccurrenceRepository.class), self);

  ReferenceTargetLookupTest() {
    when(provider.getServerContext(uri)).thenReturn(Optional.of(server));
    when(server.getDocumentNoLock(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(tree);
    when(foreign.getSymbolTree()).thenReturn(foreignTree);
    var from = mock(ModuleSymbol.class);
    when(from.getOwner()).thenReturn(document);
    when(tree.getSymbolAtPosition(any())).thenReturn(from);
  }

  @Test
  void targetLookupSkipsCallerSymbolAndRangeReconstruction() {
    var method = method(false, false);
    assertThat(index.getReference(uri, position)).get().extracting(Reference::symbol).isSameAs(method);
    clearInvocations(tree);
    assertThat(index.getTargetSymbol(uri, position)).containsSame(method);
    verify(tree, never()).getSymbolAtPosition(any());
  }

  @Test
  void privateLocalMethodRemainsVisible() {
    assertEquivalent(method(false, false));
  }

  @Test
  void foreignPrivateMethodRemainsHidden() {
    method(true, false);
    assertEquivalent(null);
  }

  @Test
  void foreignExportedMethodRemainsVisible() {
    assertEquivalent(method(true, true));
  }

  @Test
  void foreignConstructorRemainsVisibleWithoutExport() {
    var constructor = mock(ConstructorSymbol.class);
    when(constructor.getOwner()).thenReturn(foreign);
    registerMethod(constructor, true);
    assertEquivalent(constructor);
  }

  @Test
  void missingTargetIsRetriedAfterDeclarationAppears() {
    var method = method(true, true);
    when(foreignTree.getMethodSymbol("target")).thenReturn(Optional.empty());
    assertEquivalent(null);
    when(foreignTree.getMethodSymbol("target")).thenReturn(Optional.of(method));
    assertEquivalent(method);
  }

  @Test
  void selfTargetUsesTheSameResolverWithoutCallerLookup() {
    var symbol = mock(com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol.class);
    var dto = Symbol.builder().mdoRef("CommonModule.Test").moduleType(ModuleType.CommonModule)
      .scopeName(ReferenceIndex.SELF_MEMBER_SCOPE).symbolKind(SymbolKind.Property).symbolName("target").build();
    when(locations.findByPosition(uri, position)).thenReturn(Optional.of(SymbolOccurrence.of(
      OccurrenceType.REFERENCE, dto, uri, 3, 2, 3, 8)));
    when(self.resolveSelfMember(document, SymbolKind.Property, "target")).thenReturn(Optional.of(symbol));
    assertEquivalent(symbol);
    clearInvocations(tree);
    assertThat(index.getTargetSymbol(uri, position)).containsSame(symbol);
    verify(tree, never()).getSymbolAtPosition(any());
  }

  private MethodSymbol method(boolean otherDocument, boolean exported) {
    var method = mock(MethodSymbol.class);
    when(method.getOwner()).thenReturn(otherDocument ? foreign : document);
    when(method.isExport()).thenReturn(exported);
    registerMethod(method, otherDocument);
    return method;
  }

  private void registerMethod(MethodSymbol method, boolean otherDocument) {
    var dto = Symbol.builder().mdoRef("CommonModule.Test").moduleType(ModuleType.CommonModule)
      .scopeName("").symbolKind(SymbolKind.Method).symbolName("target").build();
    when(locations.findByPosition(uri, position)).thenReturn(Optional.of(SymbolOccurrence.of(
      OccurrenceType.REFERENCE, dto, uri, 3, 2, 3, 8)));
    when(server.getDocument("CommonModule.Test", ModuleType.CommonModule))
      .thenReturn(Optional.of(otherDocument ? foreign : document));
    when((otherDocument ? foreignTree : tree).getMethodSymbol("target")).thenReturn(Optional.of(method));
  }

  private void assertEquivalent(com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol expected) {
    assertThat(index.getTargetSymbol(uri, position)).isEqualTo(index.getReference(uri, position).map(Reference::symbol));
    if (expected == null) {
      assertThat(index.getTargetSymbol(uri, position)).isEmpty();
    } else {
      assertThat(index.getTargetSymbol(uri, position)).containsSame(expected);
    }
  }
}
