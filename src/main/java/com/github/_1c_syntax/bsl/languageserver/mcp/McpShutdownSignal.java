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

import java.util.concurrent.CountDownLatch;

/**
 * Сигнал завершения работы MCP-сервера.
 * <p>
 * Срабатывает при закрытии входного потока (EOF), т.е. при отключении клиента.
 * Команда запуска блокируется на {@link #await()} и завершает процесс после сигнала.
 */
public class McpShutdownSignal {

  private final CountDownLatch latch = new CountDownLatch(1);

  /**
   * Подать сигнал завершения.
   */
  public void signal() {
    latch.countDown();
  }

  /**
   * Дождаться сигнала завершения.
   */
  public void await() {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
