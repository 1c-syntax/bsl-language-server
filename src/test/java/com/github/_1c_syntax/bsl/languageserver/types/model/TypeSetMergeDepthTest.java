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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Слияние наборов не должно разворачивать структуру, вложенную саму в себя.
 */
class TypeSetMergeDepthTest {

  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");

  @Test
  void selfNestedStructureStopsGrowingDeeper() {
    // given/when: структура, значением поля которой стала она же сама, слитая с собой
    // сначала два десятка раз, а потом сотню.
    var afterFewMerges = nestingDepthAfter(20);
    var afterManyMerges = nestingDepthAfter(100);

    // then: число слияний на глубину не влияет — она встала на неподвижной точке.
    // Без ограничителя каждое слияние добавляет уровень, а копия делается на каждом
    // из них: набор растёт, пока хватает памяти.
    assertThat(afterManyMerges)
      .as("глубина вложенности полей после 100 слияний против 20")
      .isEqualTo(afterFewMerges)
      .isLessThanOrEqualTo(TypeSet.MAX_MERGE_DEPTH + 2);
  }

  @Test
  void nestedFieldsWithinLimitAreMerged() {
    // given: два набора с одноимённым полем-структурой, у которых внутри разные поля.
    var first = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Вложенная",
      TypeSet.of(STRUCTURE).withField(STRUCTURE, "Первое", TypeSet.of(STRING)));
    var second = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Вложенная",
      TypeSet.of(STRUCTURE).withField(STRUCTURE, "Второе", TypeSet.of(NUMBER)));

    // when
    var merged = first.union(second);

    // then: на посильной глубине слияние работает как раньше — поля объединяются.
    var nested = merged.getLocalFields(STRUCTURE).get("Вложенная").types();
    assertThat(nested.getLocalFields(STRUCTURE)).containsOnlyKeys("Первое", "Второе");
  }

  /**
   * Глубина вложенности структуры, поле которой заполнено ею же самой, после
   * указанного числа слияний.
   *
   * @param rounds число слияний.
   * @return глубина вложенности полей получившегося набора.
   */
  private static int nestingDepthAfter(int rounds) {
    var value = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Значение", TypeSet.of(STRING));
    for (var round = 0; round < rounds; round++) {
      value = value.union(TypeSet.of(STRUCTURE).withField(STRUCTURE, "Данные", value));
    }
    return nestingDepth(value);
  }

  /**
   * Наибольшая длина цепочки «поле — набор — его поле» в наборе.
   *
   * @param types набор типов.
   * @return число уровней вложенности полей; 0 у набора без полей.
   */
  private static int nestingDepth(TypeSet types) {
    var depth = 0;
    for (var fields : types.localFields().values()) {
      for (var field : fields.values()) {
        depth = Math.max(depth, 1 + nestingDepth(field.types()));
      }
    }
    return depth;
  }
}
