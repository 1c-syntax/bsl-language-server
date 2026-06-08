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
package com.github._1c_syntax.bsl.languageserver;

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
   * Подключён ли клиент через LSP4IJ (LSP-клиент для JetBrains IDE).
   * <p>
   * Стабильного маркера «LSP4IJ» в {@code clientInfo} нет — клиент представляется именем IDE.
   * Опознаётся по формату версии {@code "<versionName> (build <buildNumber>)"}, который LSP4IJ
   * формирует для всех JetBrains IDE.
   * <p>
   * Нужно для обхода дефекта LSP4IJ: клик по разрешённой линзе не исполняет её команду, а
   * повторно шлёт {@code codeLens/resolve} (см.
   * <a href="https://github.com/redhat-developer/lsp4ij/issues/1585">lsp4ij#1585</a>). Поэтому
   * для LSP4IJ линзы разрешаются заранее, а {@code codeLens/resolve} не анонсируется.
   *
   * @return {@code true}, если клиент — LSP4IJ; иначе {@code false}.
   */
  public boolean isLsp4ij() {
    return getClientInfo()
      .map(ClientInfo::getVersion)
      .filter(version -> version.contains("(build "))
      .isPresent();
  }
}
