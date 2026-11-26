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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.types.model.LazyTypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class FlowEnvironmentChangesTest {
  @ParameterizedTest
  @ValueSource(ints = {1, 32})
  void changedVariablesMatchFullUnionAcrossBlockEntriesAndCalls(int width) {
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var string = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    var bool = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево"));
    var array = new TypeRef(TypeKind.PLATFORM, "Массив");
    var structure = new TypeRef(TypeKind.PLATFORM, "Структура");
    var values = List.of(TypeSet.EMPTY, number, string, bool,
      TypeSet.of(array).withElement(array, number),
      TypeSet.of(array).withLazyElement(array, new LazyTypeSet("строки", () -> string)),
      TypeSet.of(structure).withField(structure, "K", number),
      TypeSet.of(structure).withField(structure, "K", string).withField(structure, "Еще", bool));
    var random = new Random(0xB51);
    var changed = new BitSet(width);
    var accumulated = FlowEnvironment.EMPTY;
    var reference = FlowEnvironment.EMPTY;
    var previous = FlowEnvironment.EMPTY;
    var current = FlowEnvironment.EMPTY;
    for (var block = 0; block < 8; block++) {
      current = FlowEnvironment.blank(width);
      for (var index = 0; index < width; index++) {
        current = current.with(index, values.get(random.nextInt(values.size())), width);
      }
      changed.set(0, width);
      for (var step = 0; step < 80; step++) {
        if (random.nextBoolean()) {
          var index = random.nextInt(width);
          var next = current.with(index, values.get(random.nextInt(values.size())), width);
          if (next != current) {
            changed.set(index);
          }
          current = next;
        }
        if (step % 3 != 0) {
          continue;
        }
        var original = current.types().clone();
        reference = reference.union(current);
        accumulated = accumulated.union(current, previous, changed);
        previous = current;
        changed.clear();
        assertThat(current.types()).containsExactly(original);
        assertThat(accumulated).as("block %s, call %s", block, step).isEqualTo(reference);
        for (var index = 0; index < width; index++) {
          assertThat(accumulated.get(index).getElementTypes(array))
            .isEqualTo(reference.get(index).getElementTypes(array));
          assertThat(accumulated.get(index).getFieldTypes("K"))
            .isEqualTo(reference.get(index).getFieldTypes("K"));
        }
      }
    }
    assertThat(current.union(accumulated)).isEqualTo(current.union(reference));
  }
}
