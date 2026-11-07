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
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import com.google.gson.Gson;
import java.util.function.Consumer;
import org.eclipse.lsp4j.jsonrpc.TracingMessageConsumer.RequestMetadata;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Подключает трассировку до валидации и дополнительных обёрток потребителя сообщений. */
final class TraceAwareLauncherBuilder extends LSPLauncher.Builder<LanguageClient> {
  private final FileAwarePrintWriter writer;
  private final Consumer<Gson> initializeAdapters;
  private final Map<String, RequestMetadata> sent = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, RequestMetadata> received = Collections.synchronizedMap(new HashMap<>());
  private final Clock clock = Clock.systemDefaultZone();
  private @Nullable MessageJsonHandler handler;

  TraceAwareLauncherBuilder(FileAwarePrintWriter writer) {
    this(writer, gson -> { });
  }

  TraceAwareLauncherBuilder(FileAwarePrintWriter writer, Consumer<Gson> initializeAdapters) {
    this.writer = writer;
    this.initializeAdapters = Objects.requireNonNull(initializeAdapters);
  }

  @Override
  protected MessageJsonHandler createJsonHandler() {
    var result = configureGson == null ? new StringBuilderMessageJsonHandler(getSupportedMethods())
      : new StringBuilderMessageJsonHandler(getSupportedMethods(), configureGson);
    result.getGson().getAdapter(CompletionList.class);
    result.getGson().getAdapter(Either.class);
    initializeAdapters.accept(result.getGson());
    handler = result;
    return result;
  }

  @Override
  protected MessageConsumer wrapMessageConsumer(MessageConsumer consumer) {
    var tracing = new TraceAwareMessageConsumer(consumer, sent, received, writer, clock);
    tracing.setJsonHandler(Objects.requireNonNull(handler));
    return super.wrapMessageConsumer(tracing);
  }
}
