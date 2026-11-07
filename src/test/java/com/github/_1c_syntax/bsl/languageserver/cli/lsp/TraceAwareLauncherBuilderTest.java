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

import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class TraceAwareLauncherBuilderTest {
  @Test
  void preparesConfiguredCompletionAdaptersWithoutSerializingAResponse() throws Exception {
    var creations = new AtomicInteger();
    var writes = new AtomicInteger();
    try (var writer = new FileAwarePrintWriter()) {
      var builder = new TraceAwareLauncherBuilder(writer);
      builder.setLocalService(new Object());
      builder.setRemoteInterface(LanguageClient.class);
      builder.configureGson(gson -> gson.registerTypeAdapterFactory(new TypeAdapterFactory() {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
          if (type.getRawType() != CompletionList.class) return null;
          creations.incrementAndGet();
          return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
              writes.incrementAndGet();
              out.value("configured");
            }

            @Override
            public T read(JsonReader in) throws IOException {
              in.skipValue();
              return null;
            }
          };
        }
      }));
      var handler = builder.createJsonHandler();
      assertThat(creations).hasValue(1);
      assertThat(writes).hasValue(0);
      var response = new ResponseMessage();
      response.setId(1);
      response.setResult(new CompletionList(false, List.of()));
      assertThat(handler.serialize(response)).contains("\"result\":\"configured\"");
      assertThat(writes).hasValue(1);
    }
  }
  private record Payload(String text) { }

  @Test
  void preparesAdditionalAdaptersOnTheActualHandlerWithoutWritingData() throws Exception {
    var initialized = new AtomicInteger();
    var creations = new AtomicInteger();
    var writes = new AtomicInteger();
    try (var writer = new FileAwarePrintWriter()) {
      var builder = new TraceAwareLauncherBuilder(writer, gson -> {
        initialized.incrementAndGet();
        gson.getAdapter(Payload.class);
      });
      builder.setLocalService(new Object());
      builder.setRemoteInterface(LanguageClient.class);
      builder.configureGson(gson -> gson.registerTypeAdapterFactory(new TypeAdapterFactory() {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
          if (type.getRawType() != Payload.class) return null;
          creations.incrementAndGet();
          return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
              writes.incrementAndGet();
              out.value("configured-payload");
            }

            @Override
            public T read(JsonReader in) throws IOException {
              in.skipValue();
              return null;
            }
          };
        }
      }));
      var handler = builder.createJsonHandler();
      assertThat(initialized).hasValue(1);
      assertThat(creations).hasValue(1);
      assertThat(writes).hasValue(0);
      var response = new ResponseMessage();
      response.setId(2);
      response.setResult(new Payload("value"));
      assertThat(handler.serialize(response)).contains("\"result\":\"configured-payload\"");
      assertThat(writes).hasValue(1);
      assertThat(creations).hasValue(1);
    }
  }

  @Test
  void preparesRawEitherResultsWithoutWritingAndKeepsCustomAdapters() throws Exception {
    var creations = new AtomicInteger();
    var writes = new AtomicInteger();
    try (var writer = new FileAwarePrintWriter()) {
      var builder = new TraceAwareLauncherBuilder(writer);
      builder.setLocalService(new Object());
      builder.setRemoteInterface(LanguageClient.class);
      builder.configureGson(gson -> gson.registerTypeAdapterFactory(new TypeAdapterFactory() {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
          if (type.getType() != Either.class) return null;
          creations.incrementAndGet();
          return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
              writes.incrementAndGet();
              out.value("configured-either");
            }

            @Override
            public T read(JsonReader in) throws IOException {
              in.skipValue();
              return null;
            }
          };
        }
      }));
      var handler = builder.createJsonHandler();
      assertThat(creations).hasValue(1);
      assertThat(writes).hasValue(0);
      var response = new ResponseMessage();
      response.setId(3);
      response.setResult(Either.forRight(new CompletionList(false, List.of())));
      assertThat(handler.serialize(response)).contains("\"result\":\"configured-either\"");
      assertThat(writes).hasValue(1);
      assertThat(creations).hasValue(1);
    }
  }

}
