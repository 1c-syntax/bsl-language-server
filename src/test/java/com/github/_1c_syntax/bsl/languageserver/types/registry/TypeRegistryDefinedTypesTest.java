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

import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ответ {@link TypeRegistry#resolveSet(String)} на имя: у обычного это один тип,
 * у определяемого — его состав. Само раскрытие состава проверяется отдельно, в
 * {@link DefinedTypesIndexTest}.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class TypeRegistryDefinedTypesTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void ordinaryNameResolvesToSingleType() {
    // given
    var ref = typeRegistry.registerConfigurationType("СправочникСсылка.Номенклатура");

    // when
    var types = typeRegistry.resolveSet("СправочникСсылка.Номенклатура");

    // then
    assertThat(types.refs()).containsExactly(ref);
  }

  @Test
  void unknownNameResolvesToNothing() {
    // given / when
    var types = typeRegistry.resolveSet("НетТакогоИмени");

    // then
    assertThat(types.refs()).isEmpty();
  }

  @Test
  void definedTypeResolvesToItsComposition() {
    // given
    var ref = typeRegistry.registerConfigurationType("СправочникСсылка.Номенклатура");
    typeRegistry.registerDefinedType("ОпределяемыйТип.Сумма",
      List.of("Число", "СправочникСсылка.Номенклатура"));

    // when
    var types = typeRegistry.resolveSet("ОпределяемыйТип.Сумма");

    // then
    assertThat(types.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("Число", "СправочникСсылка.Номенклатура");
    assertThat(types.refs()).contains(ref);
  }

  @Test
  void constituentsAreResolvedThroughTheRegistry() {
    // given: в составе есть имя, за которым в реестре типа нет
    typeRegistry.registerDefinedType("ОпределяемыйТип.Смешанный",
      List.of("Число", "НетТакогоИмени"));

    // when
    var types = typeRegistry.resolveSet("ОпределяемыйТип.Смешанный");

    // then: остаётся то, что реестр знает
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Число");
  }
}
