/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.websocket;

import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.jsonrpc.Launcher.Builder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.websocket.jakarta.WebSocketEndpoint;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Конечная точка для подключения к Language Server через websocket.
 */
@Component
@RequiredArgsConstructor
public class LSPWebSocketEndpoint extends WebSocketEndpoint<LanguageClient> {

  private final LanguageServer languageServer;
  private final List<LanguageClientAware> languageClientAwares;

  @Override
  protected void configure(Builder<LanguageClient> builder) {
    builder.setLocalService(languageServer);
    builder.setRemoteInterface(LanguageClient.class);
  }

  @Override
  protected void connect(Collection<Object> localServices, LanguageClient remoteProxy) {
    languageClientAwares.forEach(languageClientAware -> languageClientAware.connect(remoteProxy));
  }

}
