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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Проактивно запрашивает MCP-roots у клиента после первого tool-вызова.
 * <p>
 * Согласно MCP-спецификации сервер сам должен один раз дёрнуть {@code roots/list}
 * после {@code notifications/initialized}, если клиент объявил roots-capability.
 * Уведомление {@code notifications/roots/list_changed} — только для последующих
 * изменений. Если клиент (например, Claude Code 2.1.178 на Windows) объявляет
 * пустой набор и не шлёт list_changed, без проактивного запроса сервер не узнает
 * об открытых workspace-folder'ах и все workspace-зависимые tools падают
 * с «No registered workspace».
 * <p>
 * Spring AI 2.0 не предоставляет post-initialize hook, поэтому запрашиваем roots
 * лениво: при первом получении {@link McpSyncServerExchange} в любом tool-вызове.
 * Бутстрап однократный — попытка фиксируется флагом, повторных запросов не идёт.
 * Уведомления {@code roots/list_changed} (если клиент их шлёт) продолжают обрабатываться
 * штатно через {@link McpRootsChangeConsumer}.
 */
@Slf4j
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class McpRootsBootstrapper {

  private final McpRootsChangeConsumer rootsConsumer;

  private final AtomicBoolean attempted = new AtomicBoolean(false);

  /**
   * При первом вызове запрашивает у клиента {@code roots/list}, если он объявил
   * roots-capability, и передаёт ответ в {@link McpRootsChangeConsumer}. Повторные
   * вызовы — no-op (включая случаи когда клиент не поддерживает roots или запрос
   * упал с ошибкой).
   *
   * @param exchange exchange tool-вызова, через который выполняется запрос.
   */
  public void bootstrapIfNeeded(@Nullable McpSyncServerExchange exchange) {
    if (exchange == null) {
      return;
    }
    if (!attempted.compareAndSet(false, true)) {
      return;
    }
    var capabilities = exchange.getClientCapabilities();
    if (capabilities == null || capabilities.roots() == null) {
      LOGGER.debug("Skipping proactive roots/list — client does not declare roots capability");
      return;
    }
    try {
      var result = exchange.listRoots();
      if (result == null || result.roots() == null || result.roots().isEmpty()) {
        LOGGER.debug("Client returned no roots on proactive roots/list");
        return;
      }
      LOGGER.info("Proactive roots/list returned {} root(s); registering workspaces", result.roots().size());
      rootsConsumer.accept(exchange, result.roots());
    } catch (RuntimeException e) {
      LOGGER.warn("Proactive roots/list failed; workspaces will be registered only on roots/list_changed", e);
    }
  }
}
