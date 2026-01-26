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

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangesTest {

  @Test
  void testIsEmpty() {
    var emptyRangeCreate = Ranges.create();

    assertThat(Ranges.isEmpty(emptyRangeCreate)).isTrue();
    assertThat(Ranges.isEmpty(Ranges.create(1, 1, 1, 1))).isFalse();
  }

  @Test
  void testCompareDeconstructedRanges() {
    // Равные диапазоны
    assertThat(Ranges.compare(0, 0, 1, 1, 0, 0, 1, 1)).isEqualTo(0);

    // Первый диапазон меньше по начальной строке
    assertThat(Ranges.compare(0, 0, 1, 1, 1, 0, 2, 1)).isEqualTo(-1);

    // Первый диапазон больше по начальной строке
    assertThat(Ranges.compare(2, 0, 3, 1, 1, 0, 2, 1)).isEqualTo(1);

    // Одинаковые начальные строки, первый меньше по начальному символу
    assertThat(Ranges.compare(1, 0, 1, 5, 1, 5, 1, 10)).isEqualTo(-1);

    // Одинаковые начальные строки, первый больше по начальному символу
    assertThat(Ranges.compare(1, 10, 1, 15, 1, 5, 1, 10)).isEqualTo(1);

    // Одинаковые начальные позиции, первый меньше по конечной строке
    assertThat(Ranges.compare(1, 0, 1, 5, 1, 0, 2, 5)).isEqualTo(-1);

    // Одинаковые начальные позиции, первый больше по конечной строке
    assertThat(Ranges.compare(1, 0, 3, 5, 1, 0, 2, 5)).isEqualTo(1);

    // Одинаковые начальные позиции и конечные строки, первый меньше по конечному символу
    assertThat(Ranges.compare(1, 0, 2, 5, 1, 0, 2, 10)).isEqualTo(-1);

    // Одинаковые начальные позиции и конечные строки, первый больше по конечному символу
    assertThat(Ranges.compare(1, 0, 2, 15, 1, 0, 2, 10)).isEqualTo(1);
  }

  @Test
  void testContainsPositionDeconstructed() {
    // Позиция совпадает с началом диапазона
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 1, 5)).isTrue();

    // Позиция внутри диапазона (на той же строке, что и начало)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 1, 7)).isTrue();

    // Позиция внутри диапазона (на средней строке)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 2, 0)).isTrue();

    // Позиция внутри диапазона (на конечной строке, но до конечного символа)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 3, 5)).isTrue();

    // Позиция совпадает с концом диапазона (конец не включается)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 3, 10)).isFalse();

    // Позиция до начала диапазона (на той же строке)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 1, 3)).isFalse();

    // Позиция до начала диапазона (на предыдущей строке)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 0, 10)).isFalse();

    // Позиция после конца диапазона (на той же строке)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 3, 15)).isFalse();

    // Позиция после конца диапазона (на следующей строке)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, 4, 0)).isFalse();

    // Однострочный диапазон, позиция внутри
    assertThat(Ranges.containsPosition(5, 10, 5, 20, 5, 15)).isTrue();

    // Однострочный диапазон, позиция равна началу
    assertThat(Ranges.containsPosition(5, 10, 5, 20, 5, 10)).isTrue();

    // Однострочный диапазон, позиция равна концу
    assertThat(Ranges.containsPosition(5, 10, 5, 20, 5, 20)).isFalse();
  }

  @Test
  void testContainsPositionDeconstructedWithPositionObject() {
    // Позиция совпадает с началом диапазона
    assertThat(Ranges.containsPosition(1, 5, 3, 10, new Position(1, 5))).isTrue();

    // Позиция внутри диапазона
    assertThat(Ranges.containsPosition(1, 5, 3, 10, new Position(2, 0))).isTrue();

    // Позиция совпадает с концом диапазона (конец не включается)
    assertThat(Ranges.containsPosition(1, 5, 3, 10, new Position(3, 10))).isFalse();

    // Позиция до начала диапазона
    assertThat(Ranges.containsPosition(1, 5, 3, 10, new Position(0, 10))).isFalse();

    // Позиция после конца диапазона
    assertThat(Ranges.containsPosition(1, 5, 3, 10, new Position(4, 0))).isFalse();
  }
}