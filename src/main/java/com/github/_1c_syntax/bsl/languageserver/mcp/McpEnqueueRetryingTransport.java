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
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * Транспорт MCP-сессии, повторяющий отправку, которой исходный транспорт отказал из-за
 * одновременной записи.
 * <p>
 * Такой отказ — ошибка с сообщением {@value #ENQUEUE_FAILURE}. Повтор сразу же заново подписывается
 * на отправку исходного транспорта, пока соседняя запись не закончится, — в пределах
 * {@link #ENQUEUE_TIMEOUT} от первого отказа, как исправление в самом SDK. Окно рассчитано на соседа,
 * вытесненного планировщиком посреди записи; дольше ждать незачем: тем же отказом отвечает и
 * закрытый транспорт. После окна, как и при любой другой ошибке, передаётся исходная ошибка.
 * Остальные методы передаются исходному транспорту как есть.
 */
@RequiredArgsConstructor
final class McpEnqueueRetryingTransport implements McpServerTransport {

  static final String ENQUEUE_FAILURE = "Failed to enqueue message";
  static final Duration ENQUEUE_TIMEOUT = Duration.ofMillis(100);

  private final McpServerTransport delegate;

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    var send = delegate.sendMessage(message);
    return send.onErrorResume(McpEnqueueRetryingTransport::isEnqueueFailure, refusal -> Mono.defer(() -> {
      var deadline = System.nanoTime() + ENQUEUE_TIMEOUT.toNanos();
      return send.retryWhen(Retry.indefinitely()
        .filter(error -> isEnqueueFailure(error) && System.nanoTime() - deadline < 0));
    }));
  }

  private static boolean isEnqueueFailure(Throwable error) {
    return ENQUEUE_FAILURE.equals(error.getMessage());
  }

  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    return delegate.unmarshalFrom(data, typeRef);
  }

  @Override
  public Mono<Void> closeGracefully() {
    return delegate.closeGracefully();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public List<String> protocolVersions() {
    return delegate.protocolVersions();
  }
}
