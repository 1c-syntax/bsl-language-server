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
package com.github._1c_syntax.bsl.languageserver.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * Готовность общего контекста к обслуживанию MCP-запросов.
 * <p>
 * MCP-сервер (автоконфигурация Spring AI) начинает принимать запросы на старте контекста —
 * раньше, чем headless-команда {@code mcp} успеет зарегистрировать рабочее пространство и
 * проиндексировать исходники. Чтобы ранний вызов инструмента не упёрся в «нет рабочего
 * пространства», {@link McpDocumentReader} ждёт готовности.
 * <p>
 * «Взводится» только в headless-режиме ({@code app.mcp.headless=true}, выставляется при запуске
 * subcommand-а {@code mcp}). В режиме «поверх живой LSP-сессии» рабочее пространство наполняет
 * сама сессия, отдельной фазы инициализации нет — готовность сразу открыта.
 */
@Component
public class McpReadiness {

  private final CountDownLatch ready;

  public McpReadiness(@Value("${app.mcp.headless:false}") boolean headless) {
    this.ready = new CountDownLatch(headless ? 1 : 0);
  }

  /**
   * Отметить контекст готовым (вызывается после индексации).
   */
  public void markReady() {
    ready.countDown();
  }

  /**
   * Дождаться готовности контекста.
   */
  public void awaitReady() {
    try {
      ready.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for MCP context readiness", e);
    }
  }
}
