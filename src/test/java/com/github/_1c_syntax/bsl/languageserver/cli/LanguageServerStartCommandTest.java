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
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тест ветки {@code --mcp} команды {@link LanguageServerStartCommand}.
 */
class LanguageServerStartCommandTest {

  @Test
  @SuppressWarnings("unchecked")
  void mcpEnabledStartsListeningAndKeepsProcessAlive() {
    Launcher<LanguageClient> launcher = mock(Launcher.class);
    when(launcher.getRemoteProxy()).thenReturn(mock(LanguageClient.class));

    var command = new LanguageServerStartCommand(
      mock(GlobalLanguageServerConfiguration.class), launcher, List.of());
    ReflectionTestUtils.setField(command, "configurationOption", "");
    ReflectionTestUtils.setField(command, "mcpEnabled", true);
    ReflectionTestUtils.setField(command, "mcpPath", "/mcp");

    assertThat(command.call()).isEqualTo(-1);

    verify(launcher).startListening();
  }
}
