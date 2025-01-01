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
package com.github._1c_syntax.bsl.languageserver;

import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.NoArgsConstructor;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Null-safe bridge для получения возможно подключенного LanguageClient
 * в виде зависимости.
 */
@Component
@NoArgsConstructor
public class LanguageClientHolder implements LanguageClientAware {

  @Nullable
  private LanguageClient client;

  /**
   * Получить LanguageClient, если он был подключен.
   *
   * @return LanguageClient, если он был подключен.
   */
  public Optional<LanguageClient> getClient() {
    return Optional.ofNullable(client);
  }

  /**
   * LanguageClient подключен.
   *
   * @return LanguageClient подключен.
   */
  public boolean isConnected() {
    return client != null;
  }

  public void execIfConnected(Consumer<LanguageClient> consumer) {
    getClient().ifPresent(consumer);
  }

  /**
   * Выполнить подключение LanguageClient.
   * <p>
   * Метод является частью API LSP4J.
   *
   * @param client LanguageClient для подключения.
   */
  @Override
  public void connect(LanguageClient client) {
    this.client = client;
  }
}
