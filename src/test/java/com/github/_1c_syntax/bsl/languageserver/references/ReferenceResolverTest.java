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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.FAKE_DOCUMENT_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"})
@ActiveProfiles("referenceResolverTest")
@ContextConfiguration
class ReferenceResolverTest {

  @Autowired
  private ReferenceResolver referenceResolver;

  @Test
  void testReferenceResolverCyclesThroughReferenceFinders() {
    // given
    var uri = FAKE_DOCUMENT_URI;

    // when
    var optionalReference = referenceResolver.findReference(uri, new Position(0, 0));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.uri()).isEqualTo(uri))
    ;

    // when
    optionalReference = referenceResolver.findReference(uri, new Position(1, 0));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.uri()).isEqualTo(uri))
    ;

    // when
    optionalReference = referenceResolver.findReference(uri, new Position(2, 0));

    // then
    assertThat(optionalReference)
      .isEmpty()
    ;
  }

  @Test
  void testTerminalDispatchDelegatesToPositionFindersByDefault() {
    // given: finders реализуют только позиционный метод — терминальный вызов
    // должен через default-делегат резолвиться по стартовой позиции терминала.
    var uri = FAKE_DOCUMENT_URI;

    // when: терминал на первой строке (0-based line 1) — матчит firstLineReferenceFinder
    var hit = referenceResolver.findReference(uri, terminalAt(2, 0));

    // then
    assertThat(hit)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.uri()).isEqualTo(uri));

    // when: терминал на третьей строке — ни один finder не матчит
    var miss = referenceResolver.findReference(uri, terminalAt(3, 0));

    // then
    assertThat(miss).isEmpty();
  }

  @Test
  void testTerminalDispatchPrefersTerminalOverride() {
    // given: finder, переопределяющий терминальный метод; позиционный — пустой,
    // чтобы доказать, что терминальный путь идёт через override, а не спуск по позиции.
    var uri = FAKE_DOCUMENT_URI;
    var terminalAware = new ReferenceFinder() {
      @Override
      public Optional<Reference> findReference(URI ref, Position position) {
        return Optional.empty();
      }

      @Override
      public Optional<Reference> findReference(URI ref, TerminalNode terminal) {
        var reference = mock(Reference.class);
        when(reference.uri()).thenReturn(ref);
        return Optional.of(reference);
      }
    };
    var resolver = new ReferenceResolver(List.of(terminalAware));

    // when / then: терминальный путь резолвится
    assertThat(resolver.findReference(uri, terminalAt(0, 0)))
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.uri()).isEqualTo(uri));

    // а позиционный путь того же finder'а — пуст (override только терминальный)
    assertThat(resolver.findReference(uri, new Position(0, 0))).isEmpty();
  }

  @Test
  void sourceResolutionSkipsAstOnlyFindersAndKeepsFinderOrder() {
    var uri = FAKE_DOCUMENT_URI;
    var position = new Position(0, 0);
    ReferenceFinder astOnly = (ref, cursor) -> {
      throw new AssertionError("Full reference lookup must not run");
    };
    var first = mock(ReferenceFinder.class);
    var later = mock(ReferenceFinder.class);
    var symbol = mock(SourceDefinedSymbol.class);
    when(first.findSourceDefinedSymbol(uri, position)).thenReturn(Optional.of(symbol));
    when(later.findSourceDefinedSymbol(uri, position)).thenThrow(new AssertionError("Already resolved"));

    var resolver = new ReferenceResolver(List.of(astOnly, first, later));

    assertThat(resolver.findSourceDefinedSymbol(uri, position)).contains(symbol);
  }

  @Test
  void sourceResolutionDoesNotSwallowFinderFailures() {
    var uri = FAKE_DOCUMENT_URI;
    var position = new Position(0, 0);
    var finder = mock(ReferenceFinder.class);
    var failure = new IllegalStateException("Broken source index");
    doThrow(failure).when(finder).findSourceDefinedSymbol(uri, position);
    var resolver = new ReferenceResolver(List.of(finder));

    assertThatThrownBy(() -> resolver.findSourceDefinedSymbol(uri, position)).isSameAs(failure);
  }

  @Test
  void sourceResolutionPropagatesUnexpectedContentAccessIncludingOtherDocuments() {
    var uri = FAKE_DOCUMENT_URI;
    var position = new Position(0, 0);
    var finder = mock(ReferenceFinder.class);
    var resolver = new ReferenceResolver(List.of(finder));

    for (var missing : List.of(uri, URI.create("file:///another-document.bsl"))) {
      var failure = new DocumentContentUnavailableException(missing);
      doThrow(failure).when(finder).findSourceDefinedSymbol(uri, position);
      assertThatThrownBy(() -> resolver.findSourceDefinedSymbol(uri, position)).isSameAs(failure);
    }
  }

  private static TerminalNode terminalAt(int line, int charPositionInLine) {
    var token = mock(Token.class);
    when(token.getLine()).thenReturn(line);
    when(token.getCharPositionInLine()).thenReturn(charPositionInLine);
    when(token.getText()).thenReturn("x");
    var terminal = mock(TerminalNode.class);
    when(terminal.getSymbol()).thenReturn(token);
    return terminal;
  }

  @TestConfiguration
  static class Configuration {

    @Bean
    @Profile("referenceResolverTest")
    ReferenceResolver referenceResolver(
      ReferenceFinder zeroLineReferenceFinder,
      ReferenceFinder firstLineReferenceFinder
    ) {
      return new ReferenceResolver(List.of(zeroLineReferenceFinder, firstLineReferenceFinder));
    }

    @Bean
    ReferenceFinder zeroLineReferenceFinder() {
      return (uri, position) -> getReference(uri, position, 0);
    }

    @Bean
    ReferenceFinder firstLineReferenceFinder() {
      return (uri, position) -> getReference(uri, position, 1);
    }

    private Optional<Reference> getReference(URI uri, Position position, int line) {
      if (position.getLine() != line) {
        return Optional.empty();
      }
      var reference = mock(Reference.class);
      when(reference.uri()).thenReturn(uri);
      return Optional.of(reference);
    }
  }

}
