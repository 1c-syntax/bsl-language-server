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

import javax.websocket.DeploymentException;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.tyrus.server.Server;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRunner {

  private final LanguageServerConfiguration configuration;

  public void runWebSocketServer(int port) {

    String hostname = "localhost";
    String contextPath = "/";
    Server server = new Server(hostname, port, contextPath, null, BSLLSWebSocketServerConfigProvider.class);
    Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "bsl-websocket-server-shutdown-hook"));

    try {
      server.start();
      Thread.currentThread().join();
    } catch (InterruptedException e) {
      LOGGER.error("BSL websocket server has been interrupted.", e);
      Thread.currentThread().interrupt();
    } catch (DeploymentException e) {
      LOGGER.error("Cannot start BSL websocket server.", e);
    } catch (Exception e) {
      LOGGER.error("Cannot start websocket server.", e);
    } finally {
      server.stop();
    }
  }
}
