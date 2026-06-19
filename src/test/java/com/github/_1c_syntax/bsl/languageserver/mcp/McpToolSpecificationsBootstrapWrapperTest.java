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

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpToolSpecificationsBootstrapWrapperTest {

  private final McpRootsBootstrapper bootstrapper = mock(McpRootsBootstrapper.class);
  private final ObjectProvider<McpRootsBootstrapper> provider = providerOf(bootstrapper);
  private final McpToolSpecificationsBootstrapWrapper wrapper = new McpToolSpecificationsBootstrapWrapper(provider);

  private static SyncToolSpecification toolSpec(
    String name, BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler
  ) {
    var tool = Tool.builder(name, Map.<String, Object>of()).description("desc").build();
    return new SyncToolSpecification(tool, handler);
  }

  private static CallToolResult okResult() {
    var text = TextContent.builder("ok").build();
    return new CallToolResult(List.<io.modelcontextprotocol.spec.McpSchema.Content>of(text),
      false, null, Map.of());
  }

  private static CallToolRequest emptyRequest(String name) {
    return CallToolRequest.builder(name).build();
  }

  private static ObjectProvider<McpRootsBootstrapper> providerOf(McpRootsBootstrapper bootstrapper) {
    @SuppressWarnings("unchecked")
    ObjectProvider<McpRootsBootstrapper> mocked = mock(ObjectProvider.class);
    when(mocked.getObject()).thenReturn(bootstrapper);
    return mocked;
  }

  @Test
  void leavesBeansWithOtherNameUntouched() {
    var original = List.of(toolSpec("noop", (ex, req) -> okResult()));

    var processed = wrapper.postProcessAfterInitialization(original, "anotherBean");

    assertThat(processed).isSameAs(original);
    verifyNoInteractions(bootstrapper);
  }

  @Test
  void leavesNonListBeansUntouched() {
    var bean = new Object();

    var processed = wrapper.postProcessAfterInitialization(bean, "toolSpecs");

    assertThat(processed).isSameAs(bean);
    verifyNoInteractions(bootstrapper);
  }

  @Test
  void wrapsEverySpecificationAndPreservesToolMetadata() {
    var calls = new AtomicInteger();
    BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler = (ex, req) -> {
      calls.incrementAndGet();
      return okResult();
    };
    var specs = List.of(toolSpec("alpha", handler), toolSpec("beta", handler));

    @SuppressWarnings("unchecked")
    var wrapped = (List<SyncToolSpecification>) wrapper.postProcessAfterInitialization(specs, "toolSpecs");

    assertThat(wrapped).hasSize(2);
    assertThat(wrapped).extracting(SyncToolSpecification::tool).extracting(Tool::name).containsExactly("alpha", "beta");
    for (var spec : wrapped) {
      assertThat(spec.callHandler()).isNotSameAs(handler);
    }
  }

  @Test
  void invokesBootstrapBeforeDelegatingToOriginalHandler() {
    var marker = new AtomicInteger();
    doNothing().when(bootstrapper).bootstrapIfNeeded(any());

    var original = toolSpec("alpha", (ex, req) -> {
      // Bootstrap должен быть вызван к этому моменту.
      verify(bootstrapper, times(1)).bootstrapIfNeeded(ex);
      marker.set(42);
      return okResult();
    });

    @SuppressWarnings("unchecked")
    var wrapped = (List<SyncToolSpecification>)
      wrapper.postProcessAfterInitialization(List.of(original), "toolSpecs");

    var exchange = mock(McpSyncServerExchange.class);
    var result = wrapped.get(0).callHandler().apply(exchange, emptyRequest("alpha"));

    assertThat(marker.get()).isEqualTo(42);
    assertThat(result.content()).hasSize(1);
  }

  @Test
  void stillCallsOriginalHandlerIfBootstrapFails() {
    doThrow(new RuntimeException("transport down")).when(bootstrapper).bootstrapIfNeeded(any());
    var marker = new AtomicInteger();
    var original = toolSpec("alpha", (ex, req) -> {
      marker.set(1);
      return okResult();
    });

    @SuppressWarnings("unchecked")
    var wrapped = (List<SyncToolSpecification>)
      wrapper.postProcessAfterInitialization(List.of(original), "toolSpecs");
    wrapped.get(0).callHandler().apply(mock(McpSyncServerExchange.class), emptyRequest("alpha"));

    assertThat(marker.get()).isEqualTo(1);
  }

  @Test
  void emptyToolListProducesEmptyWrappedList() {
    @SuppressWarnings("unchecked")
    var wrapped = (List<SyncToolSpecification>)
      wrapper.postProcessAfterInitialization(List.<SyncToolSpecification>of(), "toolSpecs");

    assertThat(wrapped).isEmpty();
    verifyNoInteractions(bootstrapper);
  }

  @Test
  void leavesNonToolSpecElementsInTheList() {
    var stranger = "stranger";
    var spec = toolSpec("alpha", (ex, req) -> okResult());
    var source = List.of(stranger, spec);

    @SuppressWarnings("unchecked")
    var wrapped = (List<Object>) wrapper.postProcessAfterInitialization(source, "toolSpecs");

    assertThat(wrapped).hasSize(2);
    assertThat(wrapped.get(0)).isSameAs(stranger);
    assertThat(wrapped.get(1)).isInstanceOfSatisfying(SyncToolSpecification.class, w -> {
      assertThat(w.tool().name()).isEqualTo("alpha");
      assertThat(w.callHandler()).isNotSameAs(spec.callHandler());
    });
  }
}
