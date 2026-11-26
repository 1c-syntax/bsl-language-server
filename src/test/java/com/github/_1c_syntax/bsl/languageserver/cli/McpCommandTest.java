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
package com.github._1c_syntax.bsl.languageserver.cli;

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.mcp.McpShutdownSignal;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты ветвления {@link McpCommand#call()} по протоколу транспорта.
 */
class McpCommandTest {

  @SuppressWarnings("unchecked")
  private static McpCommand command(String protocol, @Nullable McpShutdownSignal shutdownSignal) {
    ObjectProvider<McpShutdownSignal> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(shutdownSignal);
    var command = new McpCommand(mock(GlobalLanguageServerConfiguration.class), provider);
    ReflectionTestUtils.setField(command, "configurationOption", "");
    ReflectionTestUtils.setField(command, "protocol", protocol);
    return command;
  }

  @Test
  void sseKeepsProcessAlive() {
    assertThat(command("sse", null).call()).isEqualTo(-1);
  }

  @Test
  void streamableKeepsProcessAlive() {
    assertThat(command("streamable", null).call()).isEqualTo(-1);
  }

  @Test
  void unknownProtocolReturnsError() {
    assertThat(command("does-not-exist", null).call()).isEqualTo(1);
  }

  @Test
  void stdioWithoutRunningServerReturnsError() {
    assertThat(command("stdio", null).call()).isEqualTo(1);
  }

  @Test
  void stdioAwaitsShutdownSignalThenReturnsZero() {
    var shutdownSignal = mock(McpShutdownSignal.class);

    assertThat(command("stdio", shutdownSignal).call()).isZero();

    verify(shutdownSignal).await();
  }
}
