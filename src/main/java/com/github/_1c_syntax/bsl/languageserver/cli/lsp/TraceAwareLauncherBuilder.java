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
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

/** Подключает трассировку до валидации и дополнительных обёрток потребителя сообщений. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
final class TraceAwareLauncherBuilder extends LSPLauncher.Builder<LanguageClient> {
  private final FileAwarePrintWriter writer;
  private final TraceAwareMessageConsumer.RequestState requests = new TraceAwareMessageConsumer.RequestState();
  private final Clock clock = Clock.systemDefaultZone();
  private @Nullable MessageJsonHandler handler;

  TraceAwareLauncherBuilder(FileAwarePrintWriter writer) {
    this.writer = writer;
  }

  @Override
  protected MessageJsonHandler createJsonHandler() {
    var result = super.createJsonHandler();
    handler = result;
    return result;
  }

  @Override
  protected MessageConsumer wrapMessageConsumer(MessageConsumer consumer) {
    var tracing = new TraceAwareMessageConsumer(consumer, requests, writer, clock);
    tracing.setJsonHandler(Objects.requireNonNull(handler));
    return super.wrapMessageConsumer(tracing);
  }
}
