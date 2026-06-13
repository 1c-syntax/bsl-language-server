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

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceSymbolResolveSupportCapabilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientCapabilitiesHolderTest {

  @Test
  void getClientInfoReturnsStoredValue() {
    // given
    var holder = new ClientCapabilitiesHolder();
    var clientInfo = new ClientInfo("Visual Studio Code", "1.0.0");
    holder.setClientInfo(clientInfo);

    // when
    var result = holder.getClientInfo();

    // then
    assertThat(result).contains(clientInfo);
  }

  @Test
  void getClientInfoIsEmptyWhenAbsent() {
    // given
    var holder = new ClientCapabilitiesHolder();

    // when
    var result = holder.getClientInfo();

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void getCapabilitiesReturnsStoredValue() {
    // given
    var holder = new ClientCapabilitiesHolder();
    var capabilities = new ClientCapabilities();
    holder.setCapabilities(capabilities);

    // when
    var result = holder.getCapabilities();

    // then
    assertThat(result).contains(capabilities);
  }

  @Test
  void getCapabilitiesIsEmptyWhenAbsent() {
    // given
    var holder = new ClientCapabilitiesHolder();

    // when
    var result = holder.getCapabilities();

    // then
    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Visual Studio Code", "Cursor", "Antigravity", "code-server"})
  void isVsCodeLikeClientIsTrueForVsCodeLikeClients(String clientName) {
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
  void isVsCodeLikeClientIsFalseForOtherClients(String clientName) {
    // given
    var holder = new ClientCapabilitiesHolder();
    holder.setClientInfo(new ClientInfo(clientName, "1.0.0"));

    // when
    var result = holder.isVsCodeLikeClient();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void isVsCodeLikeClientIsFalseWhenClientInfoIsAbsent() {
    // given
    var holder = new ClientCapabilitiesHolder();

    // when
    var result = holder.isVsCodeLikeClient();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void isWorkspaceSymbolResolveSupportedIsTrueWhenLocationRangeDeclared() {
    // given
    var holder = new ClientCapabilitiesHolder();
    holder.setCapabilities(capabilitiesWithResolveProperties(List.of("location.range")));

    // when
    var result = holder.isWorkspaceSymbolResolveSupported();

    // then
    assertThat(result).isTrue();
  }

  @Test
  void isWorkspaceSymbolResolveSupportedIsFalseWhenLocationRangeNotDeclared() {
    // given
    var holder = new ClientCapabilitiesHolder();
    holder.setCapabilities(capabilitiesWithResolveProperties(List.of("tags")));

    // when
    var result = holder.isWorkspaceSymbolResolveSupported();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void isWorkspaceSymbolResolveSupportedIsFalseWhenCapabilitiesAbsent() {
    // given
    var holder = new ClientCapabilitiesHolder();

    // when
    var result = holder.isWorkspaceSymbolResolveSupported();

    // then
    assertThat(result).isFalse();
  }

  /**
   * Собирает {@link ClientCapabilities} с заявленными свойствами
   * {@code workspace.symbol.resolveSupport.properties}.
   *
   * @param properties перечень свойств, которые клиент готов дорезолвливать
   * @return возможности клиента с заполненной секцией resolveSupport
   */
  private static ClientCapabilities capabilitiesWithResolveProperties(List<String> properties) {
    var resolveSupport = new WorkspaceSymbolResolveSupportCapabilities(properties);
    var symbolCapabilities = new SymbolCapabilities();
    symbolCapabilities.setResolveSupport(resolveSupport);
    var workspaceCapabilities = new WorkspaceClientCapabilities();
    workspaceCapabilities.setSymbol(symbolCapabilities);
    var capabilities = new ClientCapabilities();
    capabilities.setWorkspace(workspaceCapabilities);
    return capabilities;
  }
}
