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

import com.github._1c_syntax.utils.Absolute;
import io.modelcontextprotocol.spec.McpSchema.Root;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Юнит-тесты синхронизации рабочих пространств с MCP roots, включая ветки обработки ошибок.
 */
class McpRootsChangeConsumerTest {

  private final McpWorkspaceBootstrap bootstrap = mock(McpWorkspaceBootstrap.class);
  private final McpRootsChangeConsumer consumer = new McpRootsChangeConsumer(bootstrap);

  private static Root root(String dir) {
    return new Root(Absolute.path(dir).toUri().toString(), dir);
  }

  @Test
  void addsThenRemovesWorkspaceFromRoots() {
    consumer.accept(null, List.of(root("src/test/resources/cli")));
    verify(bootstrap).index(any(Path.class));

    consumer.accept(null, List.of());
    verify(bootstrap).remove(any(Path.class));
  }

  @Test
  void unsupportedRootUriIsSkipped() {
    consumer.accept(null, List.of(new Root("https://example.com/not-a-file", "bad")));

    verify(bootstrap, never()).index(any());
  }

  @Test
  void indexFailureIsSwallowedAndRootStaysUnregistered() {
    doThrow(new RuntimeException("boom")).when(bootstrap).index(any(Path.class));

    consumer.accept(null, List.of(root("src/test/resources/cli")));
    // Индексация упала -> корень не зарегистрирован, повторная синхронизация пробует снова.
    consumer.accept(null, List.of(root("src/test/resources/cli")));

    verify(bootstrap, times(2)).index(any(Path.class));
  }

  @Test
  void removeFailureStillClearsRegistration() {
    var root = root("src/test/resources/cli");
    consumer.accept(null, List.of(root));

    doThrow(new RuntimeException("boom")).when(bootstrap).remove(any(Path.class));
    consumer.accept(null, List.of());

    // Несмотря на ошибку удаления, отметка снята -> корень добавляется заново.
    consumer.accept(null, List.of(root));
    verify(bootstrap, times(2)).index(any(Path.class));
  }
}
