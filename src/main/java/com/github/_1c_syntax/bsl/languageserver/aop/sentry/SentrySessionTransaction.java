/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.aop.sentry;

import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import io.sentry.TransactionOptions;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Управляет сессионной транзакцией Sentry для всего жизненного цикла Language Server.
 * <p>
 * Создаёт одну корневую транзакцию при старте сессии, к которой привязываются
 * все дочерние спаны от LSP-запросов. Это позволяет видеть полное дерево
 * запросов в Sentry Performance.
 */
@Component
@Slf4j
public class SentrySessionTransaction {

  @Nullable
  @Getter
  private ITransaction sessionTransaction;

  /**
   * Запускает сессионную транзакцию.
   * <p>
   * Должен вызываться при инициализации Language Server.
   *
   * @param sessionName имя сессии (например, имя workspace)
   */
  public void startSession(String sessionName) {
    if (sessionTransaction != null) {
      LOGGER.warn("Session transaction already started, finishing previous one");
      finishSession();
    }

    var options = new TransactionOptions();
    options.setBindToScope(true);
    options.setIdleTimeout(null); // Не завершать по таймауту

    sessionTransaction = Sentry.startTransaction(
      "lsp-session",
      "session",
      options
    );

    sessionTransaction.setData("session.name", sessionName);
//    LOGGER.info("Started Sentry session transaction: {}", sessionName);
  }

  /**
   * Создаёт дочерний спан для LSP-запроса.
   * <p>
   * Спан автоматически привязывается к сессионной транзакции.
   *
   * @param operation название операции (например, "textDocument/hover")
   * @return созданный спан или null, если сессия не активна
   */
  @Nullable
  public ISpan startRequestSpan(String operation) {
    var currentSpan = Sentry.getSpan();
    if (currentSpan == null) {
      return null;
    }
    return currentSpan.startChild(operation);
  }

  /**
   * Завершает сессионную транзакцию.
   * <p>
   * Вызывается при shutdown Language Server.
   */
  @PreDestroy
  public void finishSession() {
    if (sessionTransaction != null) {
      sessionTransaction.setStatus(SpanStatus.OK);
      sessionTransaction.finish();
      sessionTransaction = null;
      LOGGER.info("Finished Sentry session transaction");
    }
  }
}
