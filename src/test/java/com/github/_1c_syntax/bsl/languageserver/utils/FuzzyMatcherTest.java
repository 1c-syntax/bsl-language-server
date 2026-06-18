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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzyMatcherTest {

  private final FuzzyMatcher matcher = new FuzzyMatcher();

  @Test
  void exactMatchScoresHighest() {
    // given
    var name = "Сообщить";

    // when
    var score = matcher.score(name, "сообщить");

    // then
    assertThat(score).isEqualTo(FuzzyMatcher.SCORE_EXACT);
  }

  @Test
  void prefixMatchScoresBelowExact() {
    // given
    var name = "Сообщить";

    // when
    var score = matcher.score(name, "сооб");

    // then
    assertThat(score).isEqualTo(FuzzyMatcher.SCORE_PREFIX);
  }

  @Test
  void substringInsideNameMatches() {
    // given - «общ» внутри «Сообщить», но не префикс
    var name = "Сообщить";

    // when
    var score = matcher.score(name, "общ");

    // then
    assertThat(score).isEqualTo(FuzzyMatcher.SCORE_SUBSTRING);
  }

  @Test
  void subsequenceMatchesAndRanksBelowSubstring() {
    // given - «Сбщ» — подпоследовательность «Сообщить», но не непрерывная подстрока
    var name = "Сообщить";

    // when
    var score = matcher.score(name, "сбщ");

    // then
    assertThat(score).isGreaterThanOrEqualTo(FuzzyMatcher.SCORE_SUBSEQUENCE);
    assertThat(matcher.score(name, "общ")).isLessThan(score);
  }

  @Test
  void earlierSubsequenceStartRanksBetter() {
    // given - запрос «аб» совпадает как подпоследовательность с разной позицией старта
    // when
    var earlier = matcher.score("абвгд", "ад");
    var later = matcher.score("xабгд", "ад");

    // then - более раннее начало совпадения релевантнее (меньший скор)
    assertThat(earlier).isLessThan(later);
  }

  @Test
  void noMatchReturnsSentinel() {
    // given
    var name = "Сообщить";

    // when
    var score = matcher.score(name, "xyz");

    // then
    assertThat(score).isEqualTo(FuzzyMatcher.NO_MATCH);
    assertThat(matcher.matches(name, "xyz")).isFalse();
  }

  @Test
  void matchesIsCaseInsensitiveOnName() {
    // given - имя в верхнем регистре, запрос уже в нижнем
    // when / then
    assertThat(matcher.matches("МАССИВ", "сив")).isTrue();
  }
}
