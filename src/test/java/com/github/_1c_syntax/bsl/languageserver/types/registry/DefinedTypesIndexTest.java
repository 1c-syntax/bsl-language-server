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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Хранение и раскрытие состава определяемых типов. Поиск типа по имени задаётся
 * извне, поэтому реестр здесь не нужен: в этих тестах имя типа резолвится тождественно.
 */
class DefinedTypesIndexTest {

  private DefinedTypesIndex index;

  @BeforeEach
  void setUp() {
    index = new DefinedTypesIndex();
  }

  /** Тождественный резолв: любое имя — тип с таким именем. */
  private static TypeSet asType(String name) {
    return TypeSet.of(new TypeRef(TypeKind.PLATFORM, name));
  }

  /** Резолв, не знающий ни одного имени. */
  private static TypeSet nothing(String name) {
    return TypeSet.EMPTY;
  }

  @Test
  void unknownNameIsNotADefinedType() {
    // given
    index.register("ОпределяемыйТип.Сумма", List.of("Число"));

    // when / then
    assertThat(index.knows("ОпределяемыйТип.Другой")).isFalse();
    assertThat(index.compositionOf("ОпределяемыйТип.Другой", DefinedTypesIndexTest::asType).refs())
      .isEmpty();
  }

  @Test
  void registeredNameIsKnownRegardlessOfCase() {
    // given
    index.register("ОпределяемыйТип.Сумма", List.of("Число"));

    // when / then
    assertThat(index.knows("определяемыйтип.сумма")).isTrue();
    assertThat(index.compositionOf("ОПРЕДЕЛЯЕМЫЙТИП.СУММА", DefinedTypesIndexTest::asType).refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Число");
  }

  @Test
  void compositionIsResolvedByTheGivenLookup() {
    // given
    index.register("ОпределяемыйТип.Сумма", List.of("Число", "СправочникСсылка.Номенклатура"));

    // when
    var types = index.compositionOf("ОпределяемыйТип.Сумма", DefinedTypesIndexTest::asType);

    // then
    assertThat(types.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("Число", "СправочникСсылка.Номенклатура");
  }

  @Test
  void constituentUnknownToTheLookupIsDropped() {
    // given
    index.register("ОпределяемыйТип.Сумма", List.of("Число"));

    // when: резолв не знает ни одного имени
    var types = index.compositionOf("ОпределяемыйТип.Сумма", DefinedTypesIndexTest::nothing);

    // then
    assertThat(types.refs()).isEmpty();
  }

  @Test
  void nestedDefinedTypeIsUnfoldedToOrdinaryTypes() {
    // given: один определяемый тип собран из другого
    index.register("ОпределяемыйТип.Внутренний", List.of("Строка"));
    index.register("ОпределяемыйТип.Внешний", List.of("Число", "ОпределяемыйТип.Внутренний"));

    // when
    var types = index.compositionOf("ОпределяемыйТип.Внешний", DefinedTypesIndexTest::asType);

    // then: имени определяемого типа в ответе не остаётся — только настоящие типы
    assertThat(types.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void selfReferenceIsUnfoldedOnce() {
    // given: определяемый тип, сославшийся сам на себя
    index.register("ОпределяемыйТип.Сам", List.of("Число", "ОпределяемыйТип.Сам"));

    // when
    var types = index.compositionOf("ОпределяемыйТип.Сам", DefinedTypesIndexTest::asType);

    // then: раскрытие заканчивается, а не уходит в бесконечность
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Число");
  }

  @Test
  void mutualReferenceIsUnfoldedOnce() {
    // given: два определяемых типа, ссылающихся друг на друга
    index.register("ОпределяемыйТип.Первый", List.of("Число", "ОпределяемыйТип.Второй"));
    index.register("ОпределяемыйТип.Второй", List.of("Строка", "ОпределяемыйТип.Первый"));

    // when
    var types = index.compositionOf("ОпределяемыйТип.Первый", DefinedTypesIndexTest::asType);

    // then
    assertThat(types.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void reregistrationReplacesComposition() {
    // given
    index.register("ОпределяемыйТип.Сумма", List.of("Число"));

    // when: конфигурацию перечитали, состав изменился
    index.register("ОпределяемыйТип.Сумма", List.of("Строка"));

    // then
    assertThat(index.compositionOf("ОпределяемыйТип.Сумма", DefinedTypesIndexTest::asType).refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Строка");
  }
}
