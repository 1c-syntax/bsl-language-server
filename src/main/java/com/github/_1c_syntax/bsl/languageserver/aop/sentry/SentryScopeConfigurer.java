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

import io.sentry.IScope;
import io.sentry.Sentry;
import io.sentry.protocol.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ServerInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Конфигуратор данных Sentry.
 * <p>
 * Наполняет данные информацией о приложении и пользователе.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(name = "sentryHub")
@DependsOn("sentryHub")
public class SentryScopeConfigurer {

  private final ServerInfo serverInfo;

  @PostConstruct
  public void init() {
    Sentry.configureScope((IScope scope) -> {
      var user = new User();
      user.setId(UUID.randomUUID().toString());
      scope.setUser(user);

      scope.setTag("server.version", serverInfo.getVersion());
    });
  }

}
