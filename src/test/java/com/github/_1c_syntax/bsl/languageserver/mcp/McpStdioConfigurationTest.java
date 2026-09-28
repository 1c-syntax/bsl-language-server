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

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты stdio-конфигурации MCP: сигнал завершения, обёртка входного потока с сигналом по EOF,
 * поочерёдная отправка сообщений сессии и бины.
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
  void concurrentSendsAllReachOutput() throws Exception {
    // given: сессия stdio-транспорта, которой ответы отдают сразу несколько потоков — как на
    // параллельные вызовы инструментов.
    var output = new ByteArrayOutputStream();
    var stdin = new PipedOutputStream();
    var provider = new McpStdioConfiguration.SerialSendTransportProvider(
      new JacksonMcpJsonMapper(JsonMapper.builder().build()), new PipedInputStream(stdin), output);
    var sessionTransport = new AtomicReference<McpServerTransport>();
    provider.setSessionFactory(transport -> {
      sessionTransport.set(transport);
      return mock(McpServerSession.class);
    });

    var threads = 8;
    var messagesPerThread = 200;
    var start = new CountDownLatch(1);
    var failures = new ConcurrentLinkedQueue<Throwable>();

    // when
    try (var executor = Executors.newFixedThreadPool(threads)) {
      for (var thread = 0; thread < threads; thread++) {
        executor.submit(() -> {
          start.await();
          for (var message = 0; message < messagesPerThread; message++) {
            try {
              sessionTransport.get().sendMessage(new McpSchema.JSONRPCNotification("test")).block();
            } catch (RuntimeException e) {
              failures.add(e);
            }
          }
          return null;
        });
      }
      start.countDown();
    }

    // then: ни одна отправка не получила отказа, и все сообщения записаны.
    try (stdin) {
      assertThat(failures).isEmpty();
      await().atMost(Duration.ofSeconds(10))
        .until(() -> output.toString(StandardCharsets.UTF_8).lines().count() == threads * messagesPerThread);
    }
  }

  @Test
  void serialSendTransportDelegatesToOriginal() {
    // given
    var delegate = mock(McpServerTransport.class);
    var message = new McpSchema.JSONRPCNotification("test");
    var typeRef = new TypeRef<String>() {
    };
    when(delegate.sendMessage(message)).thenReturn(Mono.empty());
    when(delegate.closeGracefully()).thenReturn(Mono.empty());
    when(delegate.protocolVersions()).thenReturn(List.of("2025-11-25"));
    when(delegate.unmarshalFrom("data", typeRef)).thenReturn("value");
    var transport = new McpStdioConfiguration.SerialSendTransport(delegate);

    // when
    transport.sendMessage(message).block();
    transport.closeGracefully().block();
    transport.close();

    // then
    assertThat(transport.protocolVersions()).containsExactly("2025-11-25");
    assertThat(transport.unmarshalFrom("data", typeRef)).isEqualTo("value");
    verify(delegate).sendMessage(message);
    verify(delegate).closeGracefully();
    verify(delegate).close();
  }

  @Test
  void serialSendTransportPassesSendFailure() {
    // given
    var delegate = mock(McpServerTransport.class);
    var message = new McpSchema.JSONRPCNotification("test");
    when(delegate.sendMessage(message)).thenReturn(Mono.error(new IllegalStateException("отказ")));
    var transport = new McpStdioConfiguration.SerialSendTransport(delegate);

    // when
    var sent = transport.sendMessage(message);

    // then
    assertThatThrownBy(sent::block).isInstanceOf(IllegalStateException.class).hasMessage("отказ");
  }

  @Test
  void beansAreInstantiated() {
    var configuration = new McpStdioConfiguration();
    var shutdownSignal = configuration.mcpShutdownSignal();

    assertThat(shutdownSignal).isNotNull();
    assertThat(configuration.stdioServerTransport(JsonMapper.builder().build(), shutdownSignal)).isNotNull();
  }
}
