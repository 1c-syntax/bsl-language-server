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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnotationRepositoryTest {

  private AnnotationRepository repository;

  @BeforeEach
  void setUp() {
    repository = new AnnotationRepository();
  }

  @Test
  void register() {
    var symbol = createAnnotationSymbol("MyAnnotation", URI.create("file:///test.bsl"));

    repository.register(symbol);

    assertThat(repository.findByName("MyAnnotation")).contains(symbol);
  }

  @Test
  void registerOverwritesSameName() {
    var uri = URI.create("file:///test.bsl");
    var first = createAnnotationSymbol("MyAnnotation", uri);
    var second = createAnnotationSymbol("MyAnnotation", uri);

    repository.register(first);
    repository.register(second);

    assertThat(repository.findByName("MyAnnotation")).contains(second);
  }

  @Test
  void findByNameReturnsEmptyForUnknown() {
    assertThat(repository.findByName("Unknown")).isEmpty();
  }

  @Test
  void removeByUri() {
    var uri1 = URI.create("file:///first.bsl");
    var uri2 = URI.create("file:///second.bsl");
    var symbol1 = createAnnotationSymbol("Anno1", uri1);
    var symbol2 = createAnnotationSymbol("Anno2", uri2);

    repository.register(symbol1);
    repository.register(symbol2);

    repository.removeByUri(uri1);

    assertThat(repository.findByName("Anno1")).isEmpty();
    assertThat(repository.findByName("Anno2")).contains(symbol2);
  }

  @Test
  void removeByUriDoesNothingForUnknownUri() {
    var symbol = createAnnotationSymbol("Anno", URI.create("file:///test.bsl"));
    repository.register(symbol);

    repository.removeByUri(URI.create("file:///other.bsl"));

    assertThat(repository.findByName("Anno")).contains(symbol);
  }

  @Test
  void clear() {
    repository.register(createAnnotationSymbol("A", URI.create("file:///a.bsl")));
    repository.register(createAnnotationSymbol("B", URI.create("file:///b.bsl")));

    repository.clear();

    assertThat(repository.findByName("A")).isEmpty();
    assertThat(repository.findByName("B")).isEmpty();
  }

  private static AnnotationSymbol createAnnotationSymbol(String name, URI ownerUri) {
    var owner = mock(DocumentContext.class);
    when(owner.getUri()).thenReturn(ownerUri);

    return AnnotationSymbol.builder()
      .name(name)
      .owner(owner)
      .range(new Range())
      .selectionRange(new Range())
      .description(Optional.empty())
      .build();
  }
}
