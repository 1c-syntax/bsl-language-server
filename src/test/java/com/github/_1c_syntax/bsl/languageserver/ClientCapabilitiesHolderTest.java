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
package com.github._1c_syntax.bsl.languageserver;

import org.eclipse.lsp4j.ClientInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientCapabilitiesHolderTest {

  @ParameterizedTest
  @CsvSource({
    "Visual Studio Code, true",
    "Cursor, true",
    "Antigravity, true",
    "code-server, true",
    "Neovim, false",
    "Some Editor, false",
    "'', false"
  })
  void staticIsVsCodeLikeClientReflectsCanonicalSet(String clientName, boolean expected) {
    // given
    // when
    var result = ClientCapabilitiesHolder.isVsCodeLikeClient(clientName);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Visual Studio Code", "Cursor", "Antigravity", "code-server"})
  void instanceIsVsCodeLikeClientIsTrueForVsCodeLikeClients(String clientName) {
    // given
    var holder = new ClientCapabilitiesHolder();
    holder.setClientInfo(new ClientInfo(clientName, "1.0.0"));

    // when
    var result = holder.isVsCodeLikeClient();

    // then
    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Neovim", "Some Editor"})
  void instanceIsVsCodeLikeClientIsFalseForOtherClients(String clientName) {
    // given
    var holder = new ClientCapabilitiesHolder();
    holder.setClientInfo(new ClientInfo(clientName, "1.0.0"));

    // when
    var result = holder.isVsCodeLikeClient();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void instanceIsVsCodeLikeClientIsFalseWhenClientInfoIsAbsent() {
    // given
    var holder = new ClientCapabilitiesHolder();

    // when
    var result = holder.isVsCodeLikeClient();

    // then
    assertThat(result).isFalse();
  }
}
