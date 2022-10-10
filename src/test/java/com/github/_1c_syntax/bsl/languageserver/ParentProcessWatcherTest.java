/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ParentProcessWatcherTest {

  @Test
  void testParentProcessIsDead() throws IOException, InterruptedException {
    // given
    var languageServer = mock(LanguageServer.class);
    var parentProcessWatcher = new ParentProcessWatcher(languageServer);

    var params = new InitializeParams();
    var process = new ProcessBuilder("timeout", "2").start();
    var pid = process.pid();
    params.setProcessId((int) pid);

    var event = new LanguageServerInitializeRequestReceivedEvent(languageServer, params);
    parentProcessWatcher.handleEvent(event);

    // when
    process.waitFor();

    // then
    await()
      .atMost(Duration.ofSeconds(1))
      .untilAsserted(
        () -> verify(languageServer, times(1)).exit()
      );
  }

  @Test
  void testParentProcessIsAlive() {
    // given
    var languageServer = mock(LanguageServer.class);
    var parentProcessWatcher = new ParentProcessWatcher(languageServer);

    var params = new InitializeParams();
    params.setProcessId((int) ProcessHandle.current().pid());

    var event = new LanguageServerInitializeRequestReceivedEvent(languageServer, params);

    // when
    parentProcessWatcher.handleEvent(event);

    // then
    await()
      .during(Duration.ofSeconds(1))
      .untilAsserted(
        () -> verify(languageServer, never()).exit()
      );
  }

}