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

// TODO: убрать вместе с McpStdioConfiguration.ConcurrentSendTransportProvider, когда stdio-транспорт
//  MCP Java SDK перестанет отказывать одновременной отправке.
//  Что происходит (SDK 2.2.0): StdioServerTransportProvider.StdioMcpSessionTransport#sendMessage кладёт
//  сообщение в приёмник через emitNext(message, busyLooping(Duration.ofMillis(100))). Если в это время
//  другой поток сам кладёт сообщение в тот же приёмник и не успевает за 100 мс (вытеснен планировщиком,
//  пауза GC), отправка завершается ошибкой "Failed to enqueue message". Лимит зашит в приватном классе
//  SDK и не настраивается.
//  java-sdk#686 закрыт как раз этим busyLooping, но отказ под нагрузкой остался: на SDK 2.2.0 тест
//  McpStdioConfigurationTest.concurrentSendsAllReachOutput, переведённый на голый
//  StdioServerTransportProvider, упал на CI (Windows, Java 25). Закрытый issue — не повод удалять обход.
//  Как проверить после обновления SDK: посмотреть, как теперь устроен sendMessage, затем перевести
//  concurrentSendsAllReachOutput на голый StdioServerTransportProvider и прогнать CI на всех ОС.
/**
 * Транспорт MCP-сессии, повторяющий отправку, которой исходный транспорт отказал из-за
 * одновременной записи.
 * <p>
 * Такой отказ — ошибка с сообщением {@value #ENQUEUE_FAILURE}. Повтор заново подписывается на
 * отправку исходного транспорта после паузы, растущей от {@link #FIRST_BACKOFF} до {@link #MAX_BACKOFF}
 * со случайным разбросом, — не больше {@value #MAX_ENQUEUE_RETRIES} раз. Паузы в сумме занимают около
 * секунды; к ним добавляется время самих попыток, каждая из которых в исходном транспорте может
 * ждать до 100 мс, — в худшем случае отправка сдаётся примерно через 3 секунды. Пауза не даёт
 * повторам крутиться вхолостую, пока соседняя запись, вытесненная планировщиком, держит приёмник, а
 * число повторов не зависит от того, сколько длится сама попытка. Дольше ждать незачем: тем же
 * отказом отвечает и закрытый транспорт. После последнего повтора, как и при любой другой ошибке,
 * передаётся исходная ошибка. Остальные методы передаются исходному транспорту как есть.
 */
@RequiredArgsConstructor
final class McpEnqueueRetryingTransport implements McpServerTransport {

  static final String ENQUEUE_FAILURE = "Failed to enqueue message";
  static final int MAX_ENQUEUE_RETRIES = 20;
  static final Duration FIRST_BACKOFF = Duration.ofMillis(1);
  static final Duration MAX_BACKOFF = Duration.ofMillis(50);

  private final McpServerTransport delegate;

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    return delegate.sendMessage(message)
      .retryWhen(Retry.backoff(MAX_ENQUEUE_RETRIES, FIRST_BACKOFF)
        .maxBackoff(MAX_BACKOFF)
        .filter(McpEnqueueRetryingTransport::isEnqueueFailure)
        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
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
