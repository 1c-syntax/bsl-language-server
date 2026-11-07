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
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.net.URI;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Буфер отдельного сообщения не разделяется между потоками и не требует синхронизации. */
final class StringBuilderMessageJsonHandler extends MessageJsonHandler {
  StringBuilderMessageJsonHandler(Map<String, JsonRpcMethod> methods) {
    this(methods, builder -> { });
  }

  StringBuilderMessageJsonHandler(Map<String, JsonRpcMethod> methods, Consumer<GsonBuilder> configure) {
    super(methods, builder -> {
      builder.registerTypeAdapterFactory(new UriEncodingFactory());
      configure.accept(builder);
    });
  }

  @Override
  public String serialize(Message message) {
    var writer = new StringBuilderWriter();
    serialize(message, writer);
    return writer.toString();
  }

  private record UriEncoding(String raw, String ascii) { }

  /** Одно чистое преобразование, ограниченное длиной; пользовательские адаптеры сохраняют приоритет. */
  private static final class UriEncodingFactory implements TypeAdapterFactory {
    @Override
    public <T> @Nullable TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      if (type.getRawType() != URI.class) {
        return null;
      }
      var delegate = gson.getDelegateAdapter(this, type);
      return new TypeAdapter<>() {
        private final AtomicReference<@Nullable UriEncoding> last = new AtomicReference<>();

        @Override
        public void write(JsonWriter out, @Nullable T value) throws IOException {
          if (value == null) {
            out.nullValue();
            return;
          }
          var uri = (URI) value;
          var raw = uri.toString();
          var known = last.get();
          String ascii;
          if (known != null && known.raw().equals(raw)) {
            ascii = known.ascii();
          } else {
            ascii = uri.toASCIIString();
            if (raw.length() <= 8192) {
              last.set(new UriEncoding(raw, ascii));
            }
          }
          out.value(ascii);
        }

        @Override
        public @Nullable T read(JsonReader in) throws IOException {
          return delegate.read(in);
        }
      };
    }
  }

  private static final class StringBuilderWriter extends Writer {
    private final StringBuilder value = new StringBuilder();
    @Override public void write(char[] chars, int offset, int length) { value.append(chars, offset, length); }
    @Override public void write(int character) { value.append((char) character); }
    @Override public void write(String text, int offset, int length) { value.append(text, offset, offset + length); }
    @Override public void write(String text) { value.append(text); }
    @Override public void flush() { }
    @Override public void close() { }
    @Override public String toString() { return value.toString(); }
  }
}
