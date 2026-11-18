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
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationPositionRepository.AnnotationPosition;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnotationPositionRepositoryTest {
  private final AnnotationPositionRepository repository = new AnnotationPositionRepository();
  private final DocumentContext document = mock(DocumentContext.class);
  private final SymbolTree tree = mock(SymbolTree.class);
  private final ModuleSymbol original = mock(ModuleSymbol.class);
  private final ModuleSymbol rebuilt = mock(ModuleSymbol.class);
  private final URI uri = URI.create("file:///annotations.os");

  @BeforeEach
  void setUp() {
    when(document.getUri()).thenReturn(uri);
    when(document.getSymbolTree()).thenReturn(tree);
    when(tree.getModule()).thenReturn(original);
  }

  @Test
  void lookupKeepsModuleAndPositionsTogetherUntilSnapshotReplacement() {
    var oldPosition = position(0);
    repository.replace(document, List.of(oldPosition));

    // Дерево уже перестроено, но слушатель ещё не опубликовал позиции нового текста.
    when(tree.getModule()).thenReturn(rebuilt);
    assertThat(repository.find(uri, new Position(0, 1))).hasValueSatisfying(match -> {
      assertThat(match.module()).isSameAs(original);
      assertThat(match.position()).isEqualTo(oldPosition);
    });

    var newPosition = position(2);
    repository.replace(document, List.of(newPosition));
    assertThat(repository.find(uri, new Position(0, 1))).isEmpty();
    assertThat(repository.find(uri, new Position(2, 1))).hasValueSatisfying(match -> {
      assertThat(match.module()).isSameAs(rebuilt);
      assertThat(match.position()).isEqualTo(newPosition);
    });
  }

  @Test
  void refreshingUnindexedDocumentDoesNotReadItsSymbolTree() {
    repository.refreshModule(document);

    verify(document, never()).getSymbolTree();
  }

  @Test
  void rebindingChangesModuleAndPreservesPositionsOfUnchangedText() {
    var position = position(0);
    repository.replace(document, List.of(position));
    when(tree.getModule()).thenReturn(rebuilt);

    repository.refreshModule(document);

    assertThat(repository.find(uri, new Position(0, 1))).hasValueSatisfying(match -> {
      assertThat(match.module()).isSameAs(rebuilt);
      assertThat(match.position()).isEqualTo(position);
    });
  }

  private static AnnotationPosition position(int line) {
    var range = Ranges.create(line, 0, 10);
    return new AnnotationPosition(range, range, "Annotation", null);
  }
}
