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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterDefinitionRangeTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 65536, Integer.MAX_VALUE})
  void rangeAndPassingModeDoNotDependOnBuilderOrder(int line) {
    var range = Ranges.create(line, 3, line, 12);
    for (var byValue : new boolean[]{false, true}) {
      var flagFirst = ParameterDefinition.builder().name("Parameter").description(Optional.empty())
        .byValue(byValue).range(range).build();
      var rangeFirst = ParameterDefinition.builder().name("Parameter").description(Optional.empty())
        .range(range).byValue(byValue).build();

      assertThat(flagFirst.getRange()).isEqualTo(range);
      assertThat(rangeFirst.getRange()).isEqualTo(range);
      assertThat(flagFirst.isByValue()).isEqualTo(byValue);
      assertThat(rangeFirst.isByValue()).isEqualTo(byValue);
      assertThat(flagFirst).isEqualTo(rangeFirst);
    }
  }

  @Test
  void builderCanChangePassingModeAndRangeRepeatedly() {
    var builder = ParameterDefinition.builder().name("Parameter").description(Optional.empty())
      .byValue(true).range(Ranges.create(0, 1, 0, 5));
    var first = builder.build();
    var second = builder.byValue(false).range(Ranges.create(42, 2, 42, 8)).build();

    assertThat(first.isByValue()).isTrue();
    assertThat(first.getRange()).isEqualTo(Ranges.create(0, 1, 0, 5));
    assertThat(second.isByValue()).isFalse();
    assertThat(second.getRange()).isEqualTo(Ranges.create(42, 2, 42, 8));
    assertThat(builder.byValue(true).build().getRange()).isEqualTo(second.getRange());
  }
}
