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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ListRootsResult;
import io.modelcontextprotocol.spec.McpSchema.Root;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверки проактивного запроса {@code roots/list} при первом tool-вызове.
 */
class McpRootsBootstrapperTest {

  private final McpRootsChangeConsumer consumer = mock(McpRootsChangeConsumer.class);
  private final McpRootsBootstrapper bootstrapper = new McpRootsBootstrapper(consumer);

  private static McpSyncServerExchange exchangeWithCapabilities(@org.jspecify.annotations.Nullable ClientCapabilities caps) {
    var exchange = mock(McpSyncServerExchange.class);
    when(exchange.getClientCapabilities()).thenReturn(caps);
    return exchange;
  }

  private static ClientCapabilities capsWithRoots() {
    return ClientCapabilities.builder().roots(true).build();
  }

  private static ClientCapabilities capsWithoutRoots() {
    return ClientCapabilities.builder().build();
  }

  @Test
  void requestsRootsAndForwardsThemToConsumer() {
    var roots = List.of(new Root("file:///D:/repo", "repo"));
    var exchange = exchangeWithCapabilities(capsWithRoots());
    when(exchange.listRoots()).thenReturn(new ListRootsResult(roots, null, null));

    bootstrapper.bootstrapIfNeeded(exchange);

    verify(exchange, times(1)).listRoots();
    verify(consumer, times(1)).accept(exchange, roots);
  }

  @Test
  void requestsRootsOnlyOnce() {
    var roots = List.of(new Root("file:///D:/repo", "repo"));
    var exchange = exchangeWithCapabilities(capsWithRoots());
    when(exchange.listRoots()).thenReturn(new ListRootsResult(roots, null, null));

    bootstrapper.bootstrapIfNeeded(exchange);
    bootstrapper.bootstrapIfNeeded(exchange);
    bootstrapper.bootstrapIfNeeded(exchange);

    verify(exchange, times(1)).listRoots();
    verify(consumer, times(1)).accept(any(), any());
  }

  @Test
  void skipsRequestWhenClientDoesNotDeclareRootsCapability() {
    var exchange = exchangeWithCapabilities(capsWithoutRoots());

    bootstrapper.bootstrapIfNeeded(exchange);

    verify(exchange, never()).listRoots();
    verify(consumer, never()).accept(any(), any());
  }

  @Test
  void skipsRequestWhenClientHasNoCapabilitiesAtAll() {
    var exchange = exchangeWithCapabilities(null);

    bootstrapper.bootstrapIfNeeded(exchange);

    verify(exchange, never()).listRoots();
    verify(consumer, never()).accept(any(), any());
  }

  @Test
  void tolerantToNullExchange() {
    bootstrapper.bootstrapIfNeeded(null);

    verify(consumer, never()).accept(any(), any());
  }

  @Test
  void swallowsListRootsFailureAndMarksAttempted() {
    var exchange = exchangeWithCapabilities(capsWithRoots());
    when(exchange.listRoots()).thenThrow(new RuntimeException("transport closed"));

    bootstrapper.bootstrapIfNeeded(exchange);
    bootstrapper.bootstrapIfNeeded(exchange);

    verify(exchange, times(1)).listRoots();
    verify(consumer, never()).accept(any(), any());
  }

  @Test
  void skipsForwardingWhenServerReturnsEmptyRoots() {
    var exchange = exchangeWithCapabilities(capsWithRoots());
    when(exchange.listRoots()).thenReturn(new ListRootsResult(List.of(), null, null));

    bootstrapper.bootstrapIfNeeded(exchange);

    verify(exchange, times(1)).listRoots();
    verify(consumer, never()).accept(eq(exchange), any());
  }
}
