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

import com.github._1c_syntax.bsl.languageserver.AutoServerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Заполняет имя и версию MCP-сервера из {@link AutoServerInfo} — единого источника истины
 * (имя из {@code spring.application.name}, версия из манифеста JAR), вместо хардкода в properties.
 * <p>
 * Реализовано как пост-обработка {@link McpServerProperties} (а не через {@code McpSyncServerCustomizer}),
 * потому что слот кастомайзера в автоконфигурации один и уже занят servlet-транспортом.
 * {@code postProcessAfterInitialization} гарантирует, что значения выставляются после биндинга
 * properties и до сборки бина {@code mcpSyncServer}, который от них зависит.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class McpServerInfoConfigurer implements BeanPostProcessor {

  private final ObjectProvider<AutoServerInfo> serverInfoProvider;

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof McpServerProperties properties) {
      var serverInfo = serverInfoProvider.getObject();
      properties.setName(serverInfo.getName());
      var version = serverInfo.getVersion();
      if (version != null && !version.isBlank()) {
        properties.setVersion(version);
      }
    }
    return bean;
  }
}
