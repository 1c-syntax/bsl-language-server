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
package com.github._1c_syntax.bsl.languageserver.types.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMetadataTest {

  @Test
  void emptyConstantHasAllFieldsBlank() {
    var e = PlatformMetadata.EMPTY;
    assertThat(e.sinceVersion()).isEmpty();
    assertThat(e.deprecatedSinceVersion()).isEmpty();
    assertThat(e.recommendedReplacements()).isEmpty();
    assertThat(e.availabilities()).isEmpty();
    assertThat(e.accessMode()).isNull();
    assertThat(e.returnValueDescription()).isSameAs(BilingualString.EMPTY);
    assertThat(e.notes()).isSameAs(BilingualString.EMPTY);
    assertThat(e.examples()).isEmpty();
    assertThat(e.seeAlso()).isEmpty();
    assertThat(e.isEmpty()).isTrue();
    assertThat(e.hasVersionInfo()).isFalse();
  }

  @Test
  void canonicalConstructorNormalizesNullsToDefaults() {
    var meta = new PlatformMetadata(
      null, null, null, null, null,
      (BilingualString) null, (BilingualString) null, null, null
    );
    assertThat(meta.sinceVersion()).isEmpty();
    assertThat(meta.deprecatedSinceVersion()).isEmpty();
    assertThat(meta.recommendedReplacements()).isEmpty();
    assertThat(meta.availabilities()).isEmpty();
    assertThat(meta.returnValueDescription()).isSameAs(BilingualString.EMPTY);
    assertThat(meta.notes()).isSameAs(BilingualString.EMPTY);
    assertThat(meta.examples()).isEmpty();
    assertThat(meta.seeAlso()).isEmpty();
    assertThat(meta.isEmpty()).isTrue();
  }

  @Test
  void compatConstructorWrapsRuStringsIntoBilingual() {
    var meta = new PlatformMetadata(
      "8.3.10", "", List.of(), Set.of(), AccessMode.READ,
      "результат", "примечание",
      List.of("ru1", "ru2"),
      List.of("См.ссылка")
    );
    assertThat(meta.returnValueDescription().ru()).isEqualTo("результат");
    assertThat(meta.returnValueDescription().en()).isEmpty();
    assertThat(meta.notes().ru()).isEqualTo("примечание");
    assertThat(meta.examples()).hasSize(2);
    assertThat(meta.examples().get(0).ru()).isEqualTo("ru1");
    assertThat(meta.examples().get(0).en()).isEmpty();
    assertThat(meta.seeAlso()).hasSize(1);
    assertThat(meta.seeAlso().get(0).ru()).isEqualTo("См.ссылка");
  }

  @Test
  void compatConstructorAcceptsNullRuLists() {
    var meta = new PlatformMetadata(
      "", "", List.of(), Set.of(), null,
      "", "", null, null
    );
    assertThat(meta.examples()).isEmpty();
    assertThat(meta.seeAlso()).isEmpty();
  }

  @Test
  void isEmptyDetectsNonEmptyFields() {
    var ver = new PlatformMetadata(
      "8.3.0", "", List.of(), Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()
    );
    assertThat(ver.isEmpty()).isFalse();
    assertThat(ver.hasVersionInfo()).isTrue();

    var dep = new PlatformMetadata(
      "", "8.3.20", List.of(), Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()
    );
    assertThat(dep.hasVersionInfo()).isTrue();

    var withReplacements = new PlatformMetadata(
      "", "", List.of("Замена"), Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()
    );
    assertThat(withReplacements.isEmpty()).isFalse();
    assertThat(withReplacements.hasVersionInfo()).isFalse();
  }
}
