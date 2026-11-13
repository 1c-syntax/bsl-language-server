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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContentUnavailableException;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformMemberReferenceFinderContentTest {

  private static final URI DOCUMENT_URI = URI.create("file:///platform-member.bsl");
  private static final Position POSITION = new Position(0, 0);

  private ServerContext server;
  private DocumentContext document;
  private TypeService types;
  private PlatformMemberReferenceFinder finder;

  @BeforeEach
  void setUp() {
    var provider = mock(ServerContextProvider.class);
    server = mock(ServerContext.class);
    document = mock(DocumentContext.class);
    types = mock(TypeService.class);
    when(provider.getDocumentNoLock(DOCUMENT_URI)).thenReturn(Optional.of(document));
    when(document.getServerContext()).thenReturn(server);
    finder = new PlatformMemberReferenceFinder(provider, types);
  }

  @Test
  void releasedDocumentDoesNotStartAstBasedLookup() {
    when(server.getDocumentState(document)).thenReturn(DocumentState.WITHOUT_CONTENT);

    assertThat(finder.findReference(DOCUMENT_URI, POSITION)).isEmpty();
    verifyNoInteractions(types);
  }

  @Test
  void unexpectedErrorsOnDocumentWithContentRemainVisible() {
    when(server.getDocumentState(document)).thenReturn(DocumentState.WITH_CONTENT);
    for (var uri : List.of(DOCUMENT_URI, URI.create("file:///another-document.bsl"))) {
      var failure = new DocumentContentUnavailableException(uri);
      doThrow(failure).when(types).memberAt(document, POSITION);
      assertThatThrownBy(() -> finder.findReference(DOCUMENT_URI, POSITION)).isSameAs(failure);
    }
  }

  @Test
  void alreadyKnownTerminalKeepsItsOwnLookupPath() {
    var terminal = mock(TerminalNode.class);
    when(types.memberAt(document, terminal)).thenReturn(Optional.empty());

    assertThat(finder.findReference(DOCUMENT_URI, terminal)).isEmpty();
    verify(types).memberAt(document, terminal);
  }
}
