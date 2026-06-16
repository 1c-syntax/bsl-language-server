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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpWorkspaceResolverTest {

  private final ServerContextProvider serverContextProvider = mock(ServerContextProvider.class);
  private final McpWorkspaceResolver resolver = new McpWorkspaceResolver(serverContextProvider);

  private static ServerContext contextOf(URI uri) {
    var ctx = mock(ServerContext.class);
    when(ctx.getWorkspaceUri()).thenReturn(uri);
    return ctx;
  }

  @Test
  void picksAnyRegisteredWorkspaceWhenRootIsNull() {
    var uri = Absolute.path("src/test/resources/cli").toUri();
    var ctx = contextOf(uri);
    when(serverContextProvider.getAllContexts()).thenReturn(Map.of(uri, ctx));

    assertThat(resolver.resolveWorkspaceUri(null)).isEqualTo(uri);
  }

  @Test
  void picksAnyRegisteredWorkspaceWhenRootIsBlank() {
    var uri = Absolute.path("src/test/resources/cli").toUri();
    var ctx = contextOf(uri);
    when(serverContextProvider.getAllContexts()).thenReturn(Map.of(uri, ctx));

    assertThat(resolver.resolveWorkspaceUri("   ")).isEqualTo(uri);
  }

  @Test
  void throwsWhenNoWorkspacesAndRootNotRequested() {
    when(serverContextProvider.getAllContexts()).thenReturn(Map.of());

    assertThatThrownBy(() -> resolver.resolveWorkspaceUri(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Open a workspace via MCP roots first");
  }

  @Test
  void resolvesExplicitRootByExactMatch() {
    var first = Absolute.path("src/test/resources/cli").toUri();
    var second = Absolute.path("src/test/resources/providers").toUri();
    Map<URI, ServerContext> contexts = new LinkedHashMap<>();
    contexts.put(first, contextOf(first));
    contexts.put(second, contextOf(second));
    when(serverContextProvider.getAllContexts()).thenReturn(contexts);

    assertThat(resolver.resolveWorkspaceUri(second.toString())).isEqualTo(second);
  }

  @Test
  void normalisesRootBeforeMatching() {
    var registered = Absolute.path("src/test/resources/cli").toUri();
    var ctx = contextOf(registered);
    when(serverContextProvider.getAllContexts()).thenReturn(Map.of(registered, ctx));

    // Та же папка, но обращаемся к ней через ./ — Absolute.uri нормализует обе формы к одному URI.
    var pathThroughDot = Absolute.path("./src/test/resources/cli").toUri().toString();

    assertThat(resolver.resolveWorkspaceUri(pathThroughDot)).isEqualTo(registered);
  }

  @Test
  void throwsWhenExplicitRootDoesNotMatchAnyRegisteredWorkspace() {
    var uri = Absolute.path("src/test/resources/cli").toUri();
    var ctx = contextOf(uri);
    when(serverContextProvider.getAllContexts()).thenReturn(Map.of(uri, ctx));

    var orphan = Absolute.path("src/test/resources/providers").toUri().toString();

    assertThatThrownBy(() -> resolver.resolveWorkspaceUri(orphan))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No registered workspace matches root");
  }
}
