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
package com.github._1c_syntax.bsl.languageserver.mcp;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Тесты stdio-конфигурации MCP: сигнал завершения, обёртка входного потока с сигналом по EOF и бины.
 */
class McpStdioConfigurationTest {

  @Test
  void shutdownSignalReleasesAwaitAfterSignal() {
    var signal = new McpShutdownSignal();
    signal.signal();

    assertTimeoutPreemptively(Duration.ofSeconds(2), signal::await);
  }

  @Test
  void eofOnReadSignalsShutdown() throws Exception {
    var signal = spy(new McpShutdownSignal());

    try (var stream = new McpStdioConfiguration.EofSignalingInputStream(
      new ByteArrayInputStream(new byte[]{42}), signal)) {
      assertThat(stream.read()).isEqualTo(42);
      assertThat(stream.read()).isEqualTo(-1);
    }

    verify(signal).signal();
  }

  @Test
  void eofOnBufferReadSignalsShutdown() throws Exception {
    var signal = spy(new McpShutdownSignal());

    try (var stream = new McpStdioConfiguration.EofSignalingInputStream(
      new ByteArrayInputStream(new byte[0]), signal)) {
      assertThat(stream.read(new byte[8], 0, 8)).isEqualTo(-1);
    }

    verify(signal).signal();
  }

  @Test
  void beansAreInstantiated() {
    var configuration = new McpStdioConfiguration();
    var shutdownSignal = configuration.mcpShutdownSignal();

    assertThat(shutdownSignal).isNotNull();
    assertThat(configuration.stdioServerTransport(JsonMapper.builder().build(), shutdownSignal)).isNotNull();
  }
}
