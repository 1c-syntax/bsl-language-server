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
package com.github._1c_syntax.bsl.languageserver.configuration.oscript;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OScriptOptionsTest {

  @Test
  void defaultShowImplicitLibraryEntriesInCompletionIsFalse() {
    // given
    // свежий OScriptOptions без явных настроек

    // when
    var options = new OScriptOptions();

    // then
    assertThat(options.isShowImplicitLibraryEntriesInCompletion())
      .as("по умолчанию implicit-записи скрываются из no-dot completion")
      .isFalse();
  }

  @Test
  void setterTogglesShowImplicitLibraryEntriesInCompletion() {
    // given
    var options = new OScriptOptions();

    // when
    options.setShowImplicitLibraryEntriesInCompletion(true);

    // then
    assertThat(options.isShowImplicitLibraryEntriesInCompletion()).isTrue();
  }

  @Test
  void defaultUseEnvLibLocationIsFalse() {
    // given
    // свежий OScriptOptions без явных настроек

    // when
    var options = new OScriptOptions();

    // then
    assertThat(options.isUseEnvLibLocation())
      .as("по умолчанию OSCRIPT_LIB_LOCATION не учитывается")
      .isFalse();
  }

  @Test
  void defaultLibRootsIsEmpty() {
    // given
    // свежий OScriptOptions без явных настроек

    // when
    var options = new OScriptOptions();

    // then
    assertThat(options.getLibRoots())
      .as("по умолчанию дополнительных корней библиотек нет")
      .isEmpty();
  }
}
