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
import org.eclipse.lsp4j.FoldingRangeCapabilities;
import org.eclipse.lsp4j.FoldingRangeSupportCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
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
   * Проверить, поддерживает ли клиент свойство {@code collapsedText} у областей сворачивания
   * (см. возможность {@code textDocument.foldingRange.foldingRange.collapsedText}, LSP 3.17).
   * <p>
   * Свойство позволяет серверу задавать осмысленный текст-заглушку свёрнутого блока вместо
   * подстановки клиентом первой строки диапазона. Если клиент не заявил поддержку, сервер
   * не должен выставлять {@link org.eclipse.lsp4j.FoldingRange#setCollapsedText(String)}.
   *
   * @return {@code true}, если клиент заявил поддержку {@code collapsedText}, иначе {@code false}.
   */
  public boolean isFoldingRangeCollapsedTextSupported() {
    return getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getFoldingRange)
      .map(FoldingRangeCapabilities::getFoldingRange)
      .map(FoldingRangeSupportCapabilities::getCollapsedText)
      .orElse(Boolean.FALSE);
  }
}
