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
package com.github._1c_syntax.bsl.languageserver.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonModuleNameFilterTest {
  @Test
  void simpleDictionaryOnlyRejectsNamesThatCannotMatch() {
    var filter = CommonModuleNameFilter.fromNames(List.of("Module", "ОбщийМодуль", "İModule"));
    for (var name : List.of("Module", "MODULE", "общиймодуль", "İModule", "i̇module", "Unknown")) {
      assertThat(filter.mayContain(name)).isTrue();
    }
    assertThat(filter.mayContain("СправочникМенеджер.Справочник1")).isFalse();
    assertThat(filter.mayContain("Module.Member")).isFalse();
    assertThat(filter.mayContain(null)).isTrue();
  }

  @Test
  void dottedOrUnknownDictionaryNamesKeepTheOriginalLookup() {
    for (var names : List.of(List.of("Module", "Dotted.Module"), Arrays.asList("Module", null))) {
      var filter = CommonModuleNameFilter.fromNames(names);
      assertThat(filter.mayContain("Dotted.Module")).isTrue();
      assertThat(filter.mayContain("СправочникМенеджер.Справочник1")).isTrue();
      assertThat(filter.mayContain("Unknown")).isTrue();
    }
  }

  @Test
  void unknownMetadataNeverRejectsAName() {
    assertThat(CommonModuleNameFilter.ALLOW_ALL.mayContain("Module.Member")).isTrue();
    assertThat(CommonModuleNameFilter.ALLOW_ALL.mayContain(null)).isTrue();
  }

  @Test
  void rebuildingTheSnapshotReflectsChangedNames() {
    var names = new ArrayList<>(List.of("Module"));
    var first = CommonModuleNameFilter.fromNames(names);
    names.add("Dotted.Module");
    var second = CommonModuleNameFilter.fromNames(names);
    assertThat(first.mayContain("Dotted.Module")).isFalse();
    assertThat(second.mayContain("Dotted.Module")).isTrue();
  }
}
