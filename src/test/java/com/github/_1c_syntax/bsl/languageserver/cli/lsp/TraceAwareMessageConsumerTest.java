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

import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.TracingMessageConsumer.RequestMetadata;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceAwareMessageConsumerTest {
  @TempDir
  Path directory;

  @Test
  void disabledNotificationsSkipFormattingAndReachTheConsumerOnce() {
    try (var f = new Fixture()) {
      var message = new NotificationMessage(); message.setMethod("probe"); message.setParams(Map.of("key", "value"));
      f.outgoing.consume(message);
      assertThat(f.outbound).containsExactly(message);
      verify(f.handler, never()).format(any());
      assertThat(f.sent).isEmpty(); assertThat(f.received).isEmpty();
    }
  }

  @Test
  void disabledRequestsKeepIndependentMetadataInBothDirections() {
    try (var f = new Fixture()) {
      f.outgoing.consume(request("7", "outgoing"));
      f.incoming.consume(request("7", "incoming"));
      assertThat(f.sent.get("7")).isEqualTo(new RequestMetadata("outgoing", Instant.EPOCH));
      assertThat(f.received.get("7")).isEqualTo(new RequestMetadata("incoming", Instant.EPOCH));
      f.incoming.consume(response("7"));
      assertThat(f.sent).isEmpty(); assertThat(f.received).hasSize(1);
      f.outgoing.consume(response("7"));
      assertThat(f.received).isEmpty();
      verify(f.handler, never()).format(any());
    }
  }

  @Test
  void enablingTracePairsAResponseWithARequestSentWhileDisabled() throws Exception {
    try (var f = new Fixture()) {
      f.outgoing.consume(request("1", "pending"));
      var file = directory.resolve("enabled.log"); f.writer.setFile(file.toFile());
      f.incoming.consume(response("1"));
      assertThat(f.sent).isEmpty();
      assertThat(Files.readString(file)).contains("Received response", "pending");
      verify(f.handler, atLeastOnce()).format(any());
    }
  }

  @Test
  void disablingTraceStillRemovesCompletedRequestMetadata() {
    try (var f = new Fixture()) {
      f.writer.setFile(directory.resolve("disabled.log").toFile());
      f.incoming.consume(request("2", "pending"));
      assertThat(f.received).hasSize(1);
      f.writer.setFile(null);
      f.outgoing.consume(response("2"));
      assertThat(f.received).isEmpty();
      assertThat(f.outbound).hasSize(1);
    }
  }

  @Test
  void missingIdsDoNotPreventDeliveryWhileDisabled() {
    try (var f = new Fixture()) {
      var request = request(null, "invalid"); var response = response(null);
      f.outgoing.consume(request); f.outgoing.consume(response);
      assertThat(f.outbound).containsExactly(request, response);
      assertThat(f.sent).isEmpty(); assertThat(f.received).isEmpty();
    }
  }

  @Test
  void enabledTraceForwardsResponsesWithUnknownIds() {
    try (var f = new Fixture()) {
      f.writer.setFile(directory.resolve("unknown-id.log").toFile());
      var response = response(null);
      f.outgoing.consume(response);
      assertThat(f.outbound).containsExactly(response);
    }
  }

  @Test
  void writerStateFollowsFileChangesAndClose() {
    var writer = new FileAwarePrintWriter();
    assertThat(writer.isEnabled()).isFalse();
    writer.setFile(directory.resolve("first.log").toFile());
    assertThat(writer.isEnabled()).isTrue();
    writer.setFile(null);
    assertThat(writer.isEnabled()).isFalse();
    writer.setFile(directory.resolve("second.log").toFile());
    writer.close();
    assertThat(writer.isEnabled()).isFalse();
  }

  private static RequestMessage request(String id, String method) {
    var message = new RequestMessage(); message.setId(id); message.setMethod(method); message.setParams(Map.of()); return message;
  }
  private static ResponseMessage response(String id) {
    var message = new ResponseMessage(); message.setId(id); message.setResult(Map.of("done", true)); return message;
  }

  private static final class Fixture implements AutoCloseable {
    private final FileAwarePrintWriter writer = new FileAwarePrintWriter();
    private final Map<String, RequestMetadata> sent = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, RequestMetadata> received = Collections.synchronizedMap(new HashMap<>());
    private final MessageJsonHandler handler = mock(MessageJsonHandler.class);
    private final List<Message> outbound = new ArrayList<>();
    private final TraceAwareMessageConsumer incoming;
    private final TraceAwareMessageConsumer outgoing;
    Fixture() {
      var clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
      incoming = new TraceAwareMessageConsumer(mock(RemoteEndpoint.class), sent, received, writer, clock);
      outgoing = new TraceAwareMessageConsumer(outbound::add, sent, received, writer, clock);
      incoming.setJsonHandler(handler); outgoing.setJsonHandler(handler);
      when(handler.format(any())).thenReturn("formatted");
    }
    public void close() { writer.close(); }
  }
}
