/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package org.github._1c_syntax.bsl.languageserver;

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.github._1c_syntax.bsl.languageserver.settings.LanguageServerSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class BSLLanguageServerTest {

  private BSLLanguageServer server;

  @BeforeEach
  void setUp() {
    server = new BSLLanguageServer(new LanguageServerSettings("en"));
  }

  @Test
  void initialize() throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();

    // when
    CompletableFuture<InitializeResult> initialize = server.initialize(params);

    // then
    // TODO
    //assertThat(initialize.get().getCapabilities()).extracting(ServerCapabilities::getCompletionProvider).isNotNull();
  }

  @Test
  void shutdown() throws ExecutionException, InterruptedException {
    CompletableFuture<Object> shutdown = server.shutdown();

    assertThat(shutdown.get()).isEqualTo(true);
  }

  @Test
  @ExpectSystemExitWithStatus(1)
  void exitWithoutShutdown() {
    // when
    server.exit();

    // then ExpectSystemExitWithStatus should not throw exception
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void exitWithShutdown() {
    // given
    server.shutdown();

    // when
    server.exit();

    // then ExpectSystemExitWithStatus should not throw exception
  }

}
