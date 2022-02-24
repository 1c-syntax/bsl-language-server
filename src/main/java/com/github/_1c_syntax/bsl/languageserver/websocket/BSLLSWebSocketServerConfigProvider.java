/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import org.springframework.stereotype.Component;

import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Collections;
import java.util.Set;

@Component
public class BSLLSWebSocketServerConfigProvider implements ServerApplicationConfig {

  private static final String WEBSOCKET_SERVER_PATH = "/bsl-language-server";

  @Override
  public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
    ServerEndpointConfig conf = ServerEndpointConfig.Builder.create(BSLLSWebSocketEndpoint.class, WEBSOCKET_SERVER_PATH).build();
    return Collections.singleton(conf);
  }

  @Override
  public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
    return scanned;
  }

}
