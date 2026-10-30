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

import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ParameterDefinitionDescriptionTest {

  @Test
  void descriptionRetainsItsValueAndCanBeClearedThroughBuilder() {
    var description = mock(ParameterDescription.class);
    var builder = ParameterDefinition.builder().name("Parameter").description(Optional.of(description));
    var described = builder.build();

    assertThat(described.getDescription()).containsSame(description);
    var undescribed = builder.description(Optional.empty()).build();
    assertThat(undescribed.getDescription()).isEmpty();
    assertThat(described.getDescription()).containsSame(description);
  }

  @Test
  void omittedDescriptionIsEmpty() {
    assertThat(ParameterDefinition.builder().name("Parameter").build().getDescription()).isEmpty();
  }
}
