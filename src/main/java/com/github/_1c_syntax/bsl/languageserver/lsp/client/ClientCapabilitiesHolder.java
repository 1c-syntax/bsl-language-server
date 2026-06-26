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
package com.github._1c_syntax.bsl.languageserver.lsp.client;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.InitializeParams;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Null-safe bridge для получения сведений о клиенте (возможности и информация о клиенте),
 * заявленных при инициализации сервера запросом
 * {@link org.eclipse.lsp4j.services.LanguageServer#initialize(InitializeParams)}.
 */
@Component
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class ClientCapabilitiesHolder {

  /**
   * Канонический набор имён ({@link ClientInfo#getName()}, оно же {@code vscode.env.appName})
   * редакторов на базе VS Code. Эти клиенты работают через VS Code extension API: ставят расширение
   * {@code language-1c-bsl} с командами-обёртками навигации и поддерживают встроенные команды запуска
   * и отладки тестов. Единый источник истины о «VS Code-совместимом клиенте» для всего сервера.
   */
  private static final Set<String> VS_CODE_LIKE_CLIENT_NAMES = Set.of(
    "Visual Studio Code",
    "Cursor",
    "Antigravity",
    "code-server"
  );

  /**
   * Возможности клиента.
   */
  @Setter
  private @Nullable ClientCapabilities capabilities;

  /**
   * Информация о клиенте (имя и версия редактора).
   */
  @Setter
  private @Nullable ClientInfo clientInfo;

  /**
   * Получить возможности клиента, если было произведено подключение клиента к серверу.
   *
   * @return Заявленные возможности клиента.
   */
  public Optional<ClientCapabilities> getCapabilities() {
    return Optional.ofNullable(capabilities);
  }

  /**
   * Получить информацию о клиенте, если она была сообщена при подключении.
   *
   * @return Информация о клиенте (имя и версия).
   */
  public Optional<ClientInfo> getClientInfo() {
    return Optional.ofNullable(clientInfo);
  }

  /**
   * Является ли подключённый (по данным {@code initialize}) клиент редактором на базе VS Code:
   * сам VS Code, Cursor, Antigravity, code-server и т.п. Такие клиенты исполняют клиентские
   * команды-обёртки расширения {@code language-1c-bsl} и поддерживают линзы запуска/отладки тестов.
   *
   * @return {@code true}, если имя подключённого клиента входит в канонический набор
   *   VS Code-совместимых редакторов; {@code false}, если клиент иной или не сообщил о себе.
   */
  public boolean isVsCodeLikeClient() {
    return getClientInfo()
      .map(ClientInfo::getName)
      .map(VS_CODE_LIKE_CLIENT_NAMES::contains)
      .orElse(false);
  }
}
