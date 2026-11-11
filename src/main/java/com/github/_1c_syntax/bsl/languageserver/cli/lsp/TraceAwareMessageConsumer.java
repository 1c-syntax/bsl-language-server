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
package com.github._1c_syntax.bsl.languageserver.cli.lsp;

import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.TracingMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Форматирует trace при включённом файле, сохраняя сведения о незавершённых запросах во всех режимах.
 */
final class TraceAwareMessageConsumer extends TracingMessageConsumer {
  private final MessageConsumer consumer;
  private final RequestState requests;
  private final FileAwarePrintWriter writer;
  private final Clock clock;
  private final boolean incoming;

  /** Незавершённые запросы одного соединения, разделённые по направлению. */
  static final class RequestState {
    final Map<String, RequestMetadata> sent = Collections.synchronizedMap(new HashMap<>());
    final Map<String, RequestMetadata> received = Collections.synchronizedMap(new HashMap<>());
  }

  TraceAwareMessageConsumer(MessageConsumer consumer, RequestState requests,
                            FileAwarePrintWriter writer, Clock clock) {
    super(consumer, requests.sent, requests.received, writer, clock);
    this.consumer = consumer;
    this.requests = requests;
    this.writer = writer;
    this.clock = clock;
    incoming = consumer instanceof RemoteEndpoint;
  }

  @Override
  public void consume(Message message) {
    if (writer.isEnabled()) {
      super.consume(message);
      return;
    }
    if (message instanceof RequestMessage request && request.getId() != null) {
      var metadata = new RequestMetadata(request.getMethod(), clock.instant());
      (incoming ? requests.received : requests.sent).put(request.getId(), metadata);
    }
    if (message instanceof ResponseMessage response && response.getId() != null) {
      (incoming ? requests.sent : requests.received).remove(response.getId());
    }
    consumer.consume(message);
  }
}
