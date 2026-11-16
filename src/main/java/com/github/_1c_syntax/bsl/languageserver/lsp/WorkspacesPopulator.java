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
package com.github._1c_syntax.bsl.languageserver.lsp;

import com.github._1c_syntax.bsl.languageserver.client.events.ClientInitializedEvent;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Наполнение контекстов рабочих областей, зарегистрированных при инициализации сервера.
 * <p>
 * Наполнение всех рабочих областей запускается асинхронно по событию {@link ClientInitializedEvent}.
 * Его окончание можно дождаться через {@link #awaitPopulated()}. Каждое событие
 * {@link LanguageServerInitializedEvent} начинает ожидание заново.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspacesPopulator {

  private final ServerContextProvider serverContextProvider;
  @Qualifier("populateContextExecutor")
  private final ExecutorService populateContextExecutor;

  private volatile CompletableFuture<Void> populated = new CompletableFuture<>();

  /**
   * Начать ожидание наполнения заново: предыдущий результат наполнения больше не учитывается.
   */
  @EventListener(LanguageServerInitializedEvent.class)
  public void handleInitializeEvent() {
    populated = new CompletableFuture<>();
  }

  /**
   * Асинхронно наполнить контексты всех зарегистрированных рабочих областей.
   * <p>
   * По окончании наполнения завершает ожидание, возвращаемое {@link #awaitPopulated()}:
   * успешно либо с ошибкой наполнения.
   */
  @EventListener(ClientInitializedEvent.class)
  public void handleClientInitializedEvent() {
    var tasks = serverContextProvider.getAllContexts().entrySet().stream()
      .map((Map.Entry<URI, ServerContext> entry) -> {
        try (var ctx = WorkspaceContextHolder.forUri(entry.getKey())) {
          return CompletableFuture.runAsync(
            entry.getValue()::populateContext,
            populateContextExecutor
          );
        }
      })
      .toArray(CompletableFuture[]::new);

    var current = populated;
    CompletableFuture.allOf(tasks)
      .whenComplete((Void unused, @Nullable Throwable throwable) -> {
        if (throwable != null) {
          LOGGER.error("Error populating workspace contexts", throwable);
          current.completeExceptionally(throwable);
        } else {
          current.complete(null);
        }
      });
  }

  /**
   * Дождаться окончания наполнения рабочих областей.
   * <p>
   * Отмена возвращённого future не прерывает наполнение и не влияет на другие ожидания.
   *
   * @return future, завершающийся по окончании наполнения (с ошибкой, если наполнение не удалось)
   */
  public CompletableFuture<Void> awaitPopulated() {
    return populated.copy();
  }
}
