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

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferenceTargetDispatchTest {
  private final URI uri = URI.create("file:///dispatch.bsl");
  private final TerminalNode terminal;

  ReferenceTargetDispatchTest() {
    var token = new CommonToken(1, "Target");
    token.setLine(4);
    token.setCharPositionInLine(2);
    terminal = new TerminalNodeImpl(token);
  }

  @Test
  void defaultTargetUsesExistingTerminalOverride() {
    var symbol = mock(Symbol.class);
    var reference = mock(Reference.class);
    when(reference.symbol()).thenReturn(symbol);
    var finder = new ReferenceFinder() {
      @Override
      public Optional<Reference> findReference(URI document, Position position) {
        throw new AssertionError("Terminal dispatch must preserve the override");
      }

      @Override
      public Optional<Reference> findReference(URI document, TerminalNode node) {
        return Optional.of(reference);
      }
    };
    assertThat(new ReferenceResolver(List.of(finder)).findTargetSymbol(uri, terminal)).containsSame(symbol);
  }

  @Test
  void firstTargetStopsTheFinderChain() {
    var empty = mock(ReferenceFinder.class);
    var first = mock(ReferenceFinder.class);
    var later = mock(ReferenceFinder.class);
    var symbol = mock(Symbol.class);
    when(first.findTargetSymbol(uri, terminal)).thenReturn(Optional.of(symbol));
    assertThat(new ReferenceResolver(List.of(empty, first, later)).findTargetSymbol(uri, terminal)).containsSame(symbol);
    verify(later, never()).findTargetSymbol(any(), any());
  }

  @Test
  void noFinderProducesNoTarget() {
    assertThat(new ReferenceResolver(List.of(mock(ReferenceFinder.class))).findTargetSymbol(uri, terminal)).isEmpty();
  }

  @Test
  void indexFinderUsesDirectTargetLookupAtTheSamePosition() {
    var index = mock(ReferenceIndex.class);
    var symbol = mock(Symbol.class);
    when(index.getTargetSymbol(uri, new Position(3, 2))).thenReturn(Optional.of(symbol));
    assertThat(new ReferenceResolver(List.of(new ReferenceIndexReferenceFinder(index))).findTargetSymbol(uri, terminal))
      .containsSame(symbol);
    verify(index, never()).getReference(eq(uri), any());
  }
}
