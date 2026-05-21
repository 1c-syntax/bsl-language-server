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

import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тестируется маппинг enum'ов bsl-context на одноимённые LS-side enum'ы.
 * Внешние enum'ы из {@code bsl-context} адресуются через полное имя по причине
 * коллизии простых имён (та же конвенция применена и в самом
 * {@link BslContextEnumMapping}).
 */
class BslContextEnumMappingTest {

  @Test
  void mapAvailabilitiesEmptyForNullOrEmpty() {
    // given
    List<com.github._1c_syntax.bsl.context.api.Availability> none = null;

    // when
    var fromNull = BslContextEnumMapping.mapAvailabilities(none);
    var fromEmpty = BslContextEnumMapping.mapAvailabilities(List.of());

    // then
    assertThat(fromNull).isEmpty();
    assertThat(fromEmpty).isEmpty();
  }

  @Test
  void mapAvailabilitiesMapsAllVariantsToLsSide() {
    // given
    var raw = List.of(
      com.github._1c_syntax.bsl.context.api.Availability.SERVER,
      com.github._1c_syntax.bsl.context.api.Availability.THIN_CLIENT,
      com.github._1c_syntax.bsl.context.api.Availability.THICK_CLIENT,
      com.github._1c_syntax.bsl.context.api.Availability.EXTERNAL_CONNECTION,
      com.github._1c_syntax.bsl.context.api.Availability.MOBILE_APPLICATION_CLIENT,
      com.github._1c_syntax.bsl.context.api.Availability.MOBILE_APPLICATION_SERVER,
      com.github._1c_syntax.bsl.context.api.Availability.MOBILE_STANDALONE_SERVER
    );

    // when
    var result = BslContextEnumMapping.mapAvailabilities(raw);

    // then
    assertThat(result).containsExactlyInAnyOrder(
      Availability.SERVER,
      Availability.THIN_CLIENT,
      Availability.THICK_CLIENT,
      Availability.EXTERNAL_CONNECTION,
      Availability.MOBILE_APPLICATION_CLIENT,
      Availability.MOBILE_APPLICATION_SERVER,
      Availability.MOBILE_STANDALONE_SERVER
    );
  }

  @Test
  void mapAccessModeReadAndReadWrite() {
    // given
    var read = com.github._1c_syntax.bsl.context.api.AccessMode.READ;
    var readWrite = com.github._1c_syntax.bsl.context.api.AccessMode.READ_WRITE;

    // when
    var readResult = BslContextEnumMapping.mapAccessMode(read);
    var readWriteResult = BslContextEnumMapping.mapAccessMode(readWrite);

    // then
    assertThat(readResult).isEqualTo(AccessMode.READ);
    assertThat(readWriteResult).isEqualTo(AccessMode.READ_WRITE);
  }

  @Test
  void mapAccessModeNullPassesThrough() {
    // given
    com.github._1c_syntax.bsl.context.api.AccessMode none = null;

    // when
    var result = BslContextEnumMapping.mapAccessMode(none);

    // then
    assertThat(result).isNull();
  }
}
