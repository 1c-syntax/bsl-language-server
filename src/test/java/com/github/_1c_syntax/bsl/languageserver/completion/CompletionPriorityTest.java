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
package com.github._1c_syntax.bsl.languageserver.completion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.AVAILABLE;
import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.PARTIAL;
import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

class CompletionPriorityTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "''      | 0",
    "0       | 0",
    "0 0     | 0",
    "2       | 2",
    "2 2     | 2",
    "1       | 1",
    "0 2     | 1",
    "2 0     | 1",
    "0 1     | 1"
  })
  void foldCombinesAvailabilityOfAlternatives(String ranks, int expected) {
    // given
    var fold = new CompletionPriority.Fold();

    // when
    Arrays.stream(ranks.split(" ")).filter(rank -> !rank.isBlank())
      .mapToInt(Integer::parseInt)
      .forEach(fold::add);

    // then
    assertThat(fold.result()).isEqualTo(expected);
    assertThat(AVAILABLE).isZero();
    assertThat(PARTIAL).isEqualTo(1);
    assertThat(UNAVAILABLE).isEqualTo(2);
  }
}
