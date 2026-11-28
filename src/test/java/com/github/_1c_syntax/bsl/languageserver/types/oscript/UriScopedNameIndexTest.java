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

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class UriScopedNameIndexTest {

  private static final URI FIRST = URI.create("file:///first.os");
  private static final URI SECOND = URI.create("file:///second.os");

  private record Entry(URI source, String label) {
  }

  @Test
  void valuesAreFoundByNameIgnoringCase() {
    // given
    var index = new UriScopedNameIndex<Entry>(Entry::source);
    var first = new Entry(FIRST, "a");
    var second = new Entry(SECOND, "b");

    // when
    index.add("Лог", first);
    index.add("ЛОГ", second);

    // then
    assertThat(index.get("лог")).containsExactlyInAnyOrder(first, second);
    assertThat(index.get("Другой")).isEmpty();
  }

  @Test
  void removeByUriDropsOnlyThatDocumentsValuesAndEmptyNames() {
    // given
    var index = new UriScopedNameIndex<Entry>(Entry::source);
    var shared = new Entry(SECOND, "shared");
    index.add("Общее", new Entry(FIRST, "own"));
    index.add("Общее", shared);
    index.add("Своё", new Entry(FIRST, "alone"));

    // when
    index.removeByUri(FIRST);

    // then
    assertThat(index.get("Общее")).containsExactly(shared);
    assertThat(index.get("Своё")).isEmpty();
  }

  @Test
  void namesOfDocumentAreTheLowercaseNamesItAddedUnder() {
    // given
    var index = new UriScopedNameIndex<Entry>(Entry::source);
    index.add("Лог", new Entry(FIRST, "a"));
    index.add("Прозвище", new Entry(FIRST, "a"));
    index.add("Чужое", new Entry(SECOND, "b"));

    // then
    assertThat(index.namesOf(FIRST)).containsExactlyInAnyOrder("лог", "прозвище");
    assertThat(index.namesOf(URI.create("file:///none.os"))).isEmpty();
  }

  @Test
  void clearForgetsEverything() {
    // given
    var index = new UriScopedNameIndex<URI>(uri -> uri);
    index.add("Родитель", FIRST);

    // when
    index.clear();
    index.removeByUri(FIRST);

    // then
    assertThat(index.get("Родитель")).isEmpty();
  }
}
