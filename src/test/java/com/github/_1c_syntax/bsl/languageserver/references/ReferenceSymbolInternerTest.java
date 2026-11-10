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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceBeanScope;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrence;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrenceRepository;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReferenceSymbolInternerTest {

  private static final URI FIRST_WORKSPACE = URI.create("file:///reference-symbol-interner-first/");
  private static final URI SECOND_WORKSPACE = URI.create("file:///reference-symbol-interner-second/");
  private static final URI DOCUMENT = URI.create("file:///reference-symbol-interner/Module.bsl");

  @Autowired
  private ReferenceIndex index;

  @Autowired
  private SymbolOccurrenceRepository repository;

  @Autowired
  private WorkspaceBeanScope workspaceScope;

  @AfterEach
  void removeWorkspaces() {
    workspaceScope.removeWorkspace(FIRST_WORKSPACE);
    workspaceScope.removeWorkspace(SECOND_WORKSPACE);
  }

  @Test
  void symbolsAreCanonicalBeforeAndAfterPublishingOccurrences() {
    try (var ignored = WorkspaceContextHolder.forUri(FIRST_WORKSPACE, "first")) {
      var first = occurrence();
      assertThat(occurrence().symbol()).isSameAs(first.symbol());
      assertThat(repository.getAllBySymbol(first.symbol())).isEmpty();

      index.replaceReferences(DOCUMENT, List.of(first));

      assertThat(repository.getAllBySymbol(first.symbol())).containsExactly(first);
      assertThat(occurrence().symbol()).isSameAs(first.symbol());
    }
  }

  @Test
  void clearingOneWorkspaceReleasesItsSymbolsAndPreservesAnotherWorkspace() throws Exception {
    var first = WorkspaceContextHolder.call(FIRST_WORKSPACE, "first", this::occurrence);
    var second = WorkspaceContextHolder.call(SECOND_WORKSPACE, "second", this::occurrence);
    assertThat(second.symbol()).isEqualTo(first.symbol()).isNotSameAs(first.symbol());

    try (var ignored = WorkspaceContextHolder.forUri(FIRST_WORKSPACE, "first")) {
      assertThat(occurrence().symbol()).isSameAs(first.symbol());
      index.clear();
      assertThat(occurrence().symbol()).isEqualTo(first.symbol()).isNotSameAs(first.symbol());
    }

    assertThat(WorkspaceContextHolder.call(SECOND_WORKSPACE, "second", this::occurrence).symbol()).isSameAs(second.symbol());
  }

  @Test
  void removingWorkspaceDropsItsCanonicalSymbols() throws Exception {
    var first = WorkspaceContextHolder.call(FIRST_WORKSPACE, "first", this::occurrence);

    workspaceScope.removeWorkspace(FIRST_WORKSPACE);

    var reopened = WorkspaceContextHolder.call(FIRST_WORKSPACE, "first", this::occurrence);
    assertThat(reopened.symbol()).isEqualTo(first.symbol()).isNotSameAs(first.symbol());
  }

  private SymbolOccurrence occurrence() {
    return index.methodCallOccurrence(DOCUMENT, "CommonModule.Example", ModuleType.CommonModule,
      "Method", Ranges.create(0, 0, 6));
  }
}
