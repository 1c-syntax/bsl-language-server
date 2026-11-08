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

import com.github._1c_syntax.bsl.languageserver.completion.CompletionData;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.google.gson.Gson;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class CompletionSerializationInitializationTest {
  @Test
  void preparationPreservesDefaultPayloadBytesAndRoundTrip() {
    var expected = new MessageJsonHandler(Map.of()).getGson();
    var prepared = new StringBuilderMessageJsonHandler(Map.of()).getGson();
    var data = CompletionData.forFunction(URI.create("file:///workspace/Модуль.bsl"), "Формат", FileType.BSL, Language.RU);
    data.setContextDescription("Описание контекста");
    var encoded = prepared.toJson(data);
    assertThat(encoded).isEqualTo(expected.toJson(data));
    assertThat(prepared.fromJson(encoded, CompletionData.class))
      .isEqualTo(expected.fromJson(encoded, CompletionData.class));
  }

  @Test
  void preparationUsesConfiguredAdaptersWithoutSerializing() {
    var creations = new AtomicInteger();
    var writes = new AtomicInteger();
    var gson = new StringBuilderMessageJsonHandler(Map.of(), builder -> builder.registerTypeAdapterFactory(new TypeAdapterFactory() {
      @Override
      public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != CompletionData.class) return null;
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
    })).getGson();
    assertThat(creations).hasValue(0);
    assertThat(writes).hasValue(0);
    assertThat(gson.toJson(new CompletionData())).isEqualTo("\"configured\"");
    assertThat(creations).hasValue(1);
    assertThat(writes).hasValue(1);
  }
}
