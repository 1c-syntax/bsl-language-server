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

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Оборачивает каждый {@link SyncToolSpecification}, собранный Spring AI MCP по
 * {@code @McpTool}-методам, в обёртку, которая перед делегированием в исходный
 * handler вызывает {@link McpRootsBootstrapper}. Это даёт проактивный
 * {@code roots/list} (как требует MCP-спека) без какого-либо знания о
 * bootstrap-логике в самих tool-методах.
 * <p>
 * Spring AI autoconfig публикует список как {@code @Bean public List<SyncToolSpecification> toolSpecs(...)}
 * в {@code McpServerSpecificationFactoryAutoConfiguration}; имя bean'а — {@code "toolSpecs"}.
 * Мы перехватываем post-init этого bean'а и подменяем его на список обёрток.
 * <p>
 * Зависимость на {@link McpRootsBootstrapper} ленивая через {@link ObjectProvider},
 * чтобы не вынуждать создание workspace-scoped бина в момент инициализации
 * {@code BeanPostProcessor}'а.
 */
@Slf4j
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class McpToolSpecificationsBootstrapWrapper implements BeanPostProcessor {

  private static final String SPRING_AI_TOOL_SPECS_BEAN_NAME = "toolSpecs";

  private final ObjectProvider<McpRootsBootstrapper> rootsBootstrapper;

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!SPRING_AI_TOOL_SPECS_BEAN_NAME.equals(beanName) || !(bean instanceof List<?> list)) {
      return bean;
    }
    return list.stream()
      .map(item -> item instanceof SyncToolSpecification spec ? wrap(spec) : item)
      .toList();
  }

  private SyncToolSpecification wrap(SyncToolSpecification original) {
    return new SyncToolSpecification(original.tool(), wrapHandler(original.callHandler()));
  }

  private BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> wrapHandler(
    BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> original
  ) {
    return (exchange, request) -> {
      try {
        rootsBootstrapper.getObject().bootstrapIfNeeded(exchange);
      } catch (RuntimeException e) {
        // Никогда не валим tool-вызов из-за проблем bootstrap'а — tools без зарегистрированных
        // workspace'ов сами кинут понятное «No registered workspace».
        LOGGER.warn("Failed to bootstrap MCP roots before tool call", e);
      }
      return original.apply(exchange, request);
    };
  }
}
