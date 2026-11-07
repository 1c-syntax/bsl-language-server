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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class StringBuilderMessageJsonHandlerTest {
  @ParameterizedTest
  @MethodSource("payloads")
  void keepsNativeJsonAndUtf8Bytes(Message message, boolean customized) {
    Consumer<GsonBuilder> configure = builder -> builder.serializeNulls().disableHtmlEscaping().setPrettyPrinting();
    var original = customized ? new MessageJsonHandler(Map.of(), configure) : new MessageJsonHandler(Map.of());
    var replacement = customized ? new StringBuilderMessageJsonHandler(Map.of(), configure)
      : new StringBuilderMessageJsonHandler(Map.of());
    var expected = original.serialize(message);
    var actual = replacement.serialize(message);
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.getBytes(StandardCharsets.UTF_8)).containsExactly(expected.getBytes(StandardCharsets.UTF_8));
  }

  static Stream<Arguments> payloads() {
    return messages().stream().flatMap(message -> Stream.of(Arguments.of(message, false), Arguments.of(message, true)));
  }

  @Test
  void concurrentMessagesHaveIndependentBuffers() throws Exception {
    var original = new MessageJsonHandler(Map.of());
    var replacement = new StringBuilderMessageJsonHandler(Map.of());
    var messages = messages();
    var expected = messages.stream().map(original::serialize).toList();
    try (var executor = Executors.newFixedThreadPool(4)) {
      var tasks = new ArrayList<Callable<Void>>();
      for (var worker = 0; worker < 8; worker++) tasks.add(() -> {
        for (var repetition = 0; repetition < 30; repetition++) {
          for (var index = 0; index < messages.size(); index++) {
            assertThat(replacement.serialize(messages.get(index))).isEqualTo(expected.get(index));
          }
        }
        return null;
      });
      for (var result : executor.invokeAll(tasks)) result.get(5, TimeUnit.SECONDS);
    }
  }


  @Test
  void customUriSerializerKeepsItsPerOccurrenceBehavior() {
    Consumer<GsonBuilder> configure = builder -> {
      var count = new AtomicInteger();
      builder.registerTypeAdapter(URI.class, (JsonSerializer<URI>) (value, type, context) ->
        new JsonPrimitive("custom-" + count.incrementAndGet() + ":" + value));
    };
    var original = new MessageJsonHandler(Map.of(), configure);
    var replacement = new StringBuilderMessageJsonHandler(Map.of(), configure);
    var uri = URI.create("file:///G:/Каталог/Модуль.bsl");
    var message = response(List.of(uri, uri, URI.create(uri.toString())));
    assertThat(replacement.serialize(message)).isEqualTo(original.serialize(message));
    assertThat(replacement.serialize(message)).isEqualTo(original.serialize(message));
  }

  @Test
  void nativeUriReadingIncludingTheStringNullIsUnchanged() {
    var original = new MessageJsonHandler(Map.of());
    var replacement = new StringBuilderMessageJsonHandler(Map.of());
    for (var json : List.of("null", "\"null\"", "\"file:///G:/a%20b.bsl\"", "\"https://example.test/Путь\"")) {
      assertThat(Objects.equals(original.getGson().fromJson(json, URI.class),
        replacement.getGson().fromJson(json, URI.class))).isTrue();
    }
  }

  private static List<Message> messages() {
    var item = new CompletionItem("Ёлка🙂\\\"\n");
    item.setKind(CompletionItemKind.Method);
    item.setDetail("<tag> & 'quotes'");
    item.setData(Map.of("type", "СправочникСсылка.Пользователи", "number", 1));
    var completion = response(new CompletionList(false, List.of(item)));
    var unicode = response("Ёλ🙂\uD800\n\t\\\"<>&");
    var empty = response(null);
    var error = new ResponseMessage(); error.setId("error");
    error.setError(new ResponseError(ResponseErrorCode.InvalidParams, "Ошибка🙂", Map.of("key", "value")));
    var request = new RequestMessage(); request.setId("request"); request.setMethod("probe");
    request.setParams(Map.of("value", -0.0, "flag", true));
    var notification = new NotificationMessage(); notification.setMethod("probe");
    notification.setParams(Map.of("list", List.of(1, 2, 3)));
    var diagnostic = new Diagnostic(new Range(new Position(1, 2), new Position(1, 7)), "Недоступный метод");
    diagnostic.setSeverity(DiagnosticSeverity.Error); diagnostic.setCode("MethodCallUnavailable");
    var published = new NotificationMessage(); published.setMethod("textDocument/publishDiagnostics");
    published.setParams(new PublishDiagnosticsParams("file:///module.bsl", List.of(diagnostic), 7));
    var uri = URI.create("file:///G:/Каталог/Модуль🙂.bsl");
    var repeatedUris = new ArrayList<URI>();
    for (var index = 0; index < 32; index++) repeatedUris.add(uri);
    repeatedUris.add(null);
    repeatedUris.add(URI.create("https://example.test/a%20b?q=%2F#fragment"));
    var uriResponse = response(repeatedUris);
    var longUriResponse = response(URI.create("https://example.test/" + "Я".repeat(9000)));
    return List.of(completion, unicode, empty, error, request, notification, published, uriResponse, longUriResponse);
  }

  private static ResponseMessage response(Object value) {
    var response = new ResponseMessage(); response.setId("response"); response.setResult(value); return response;
  }
}
