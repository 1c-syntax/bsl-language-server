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
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты транспорта MCP-сессии, повторяющего отправку после отказа из-за одновременной записи.
 */
class McpEnqueueRetryingTransportTest {

  private static final McpSchema.JSONRPCMessage MESSAGE = new McpSchema.JSONRPCNotification("test");

  @Test
  void delegatesToOriginalTransport() {
    // given
    var delegate = mock(McpServerTransport.class);
    var typeRef = new TypeRef<String>() {
    };
    when(delegate.sendMessage(MESSAGE)).thenReturn(Mono.empty());
    when(delegate.closeGracefully()).thenReturn(Mono.empty());
    when(delegate.protocolVersions()).thenReturn(List.of("2025-11-25"));
    when(delegate.unmarshalFrom("data", typeRef)).thenReturn("value");
    var transport = new McpEnqueueRetryingTransport(delegate);

    // when
    transport.sendMessage(MESSAGE).block();
    transport.closeGracefully().block();
    transport.close();

    // then
    assertThat(transport.protocolVersions()).containsExactly("2025-11-25");
    assertThat(transport.unmarshalFrom("data", typeRef)).isEqualTo("value");
    verify(delegate).sendMessage(MESSAGE);
    verify(delegate).closeGracefully();
    verify(delegate).close();
  }

  @Test
  void repeatsRefusedEnqueue() {
    // given: первая попытка отправки получает отказ из-за одновременной записи.
    var attempts = new AtomicInteger();
    var transport = transportSending(Mono.defer(() -> attempts.getAndIncrement() == 0
      ? Mono.error(new RuntimeException(McpEnqueueRetryingTransport.ENQUEUE_FAILURE))
      : Mono.empty()));

    // when
    transport.sendMessage(MESSAGE).block();

    // then
    assertThat(attempts).hasValue(2);
  }

  @Test
  void waitsWhileSinkIsHeld() {
    // given: соседняя запись держит приёмник 30 мс — например, её поток вытеснен планировщиком.
    var releasedAt = System.nanoTime() + Duration.ofMillis(30).toNanos();
    var transport = transportSending(Mono.defer(() -> System.nanoTime() - releasedAt < 0
      ? Mono.error(new RuntimeException(McpEnqueueRetryingTransport.ENQUEUE_FAILURE))
      : Mono.empty()));

    // when
    var sent = transport.sendMessage(MESSAGE);

    // then: отправка дождалась освобождения, а не сдалась раньше.
    assertThatCode(sent::block).doesNotThrowAnyException();
  }

  @Test
  void passesOtherFailureAtOnce() {
    // given
    var attempts = new AtomicInteger();
    var transport = transportSending(Mono.defer(() -> {
      attempts.incrementAndGet();
      return Mono.error(new IllegalStateException("отказ"));
    }));

    // when
    var sent = transport.sendMessage(MESSAGE);

    // then
    assertThatThrownBy(sent::block).isInstanceOf(IllegalStateException.class).hasMessage("отказ");
    assertThat(attempts).hasValue(1);
  }

  @Test
  void givesUpOnPersistentRefusal() {
    // given: отказывает каждая попытка — так отвечает закрытый транспорт.
    var attempts = new AtomicInteger();
    var firstRefusal = new AtomicLong();
    var transport = transportSending(Mono.defer(() -> {
      if (attempts.getAndIncrement() == 0) {
        firstRefusal.set(System.nanoTime());
      }
      return Mono.error(new RuntimeException(McpEnqueueRetryingTransport.ENQUEUE_FAILURE));
    }));

    // when
    var sent = transport.sendMessage(MESSAGE);

    // then: повторы шли всё окно от первого отказа, после него — исходная ошибка.
    assertThatThrownBy(sent::block).hasMessage(McpEnqueueRetryingTransport.ENQUEUE_FAILURE);
    assertThat(Duration.ofNanos(System.nanoTime() - firstRefusal.get()))
      .isGreaterThanOrEqualTo(McpEnqueueRetryingTransport.ENQUEUE_TIMEOUT);
    assertThat(attempts).hasValueGreaterThan(1);
  }

  private static McpServerTransport transportSending(Mono<Void> send) {
    var delegate = mock(McpServerTransport.class);
    when(delegate.sendMessage(any())).thenReturn(send);
    return new McpEnqueueRetryingTransport(delegate);
  }
}
