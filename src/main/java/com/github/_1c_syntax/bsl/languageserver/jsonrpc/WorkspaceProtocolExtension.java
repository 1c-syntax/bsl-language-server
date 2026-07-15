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
package com.github._1c_syntax.bsl.languageserver.jsonrpc;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

import java.util.concurrent.CompletableFuture;

/**
 * Расширения протокола LSP уровня рабочей области (workspace).
 * <p>
 * Содержит дополнительные методы JSON-RPC {@code workspace/*}, не входящие в стандартную
 * спецификацию LSP.
 */
public interface WorkspaceProtocolExtension {

  /**
   * Запрос на построение дерева конфигурации рабочей области (расширение протокола).
   * <p>
   * Возвращает объекты метаданных верхнего уровня в разрезе основной конфигурации и её
   * расширений: с именами, синонимами, реквизитами и стандартными реквизитами.
   *
   * @param params Параметры запроса (обязателен {@code workspaceUri} либо {@code workspaceName}).
   * @return Дерево конфигурации рабочей области.
   */
  @JsonRequest(
    value = "workspace/x-configurationTree",
    useSegment = false
  )
  CompletableFuture<ConfigurationTree> configurationTree(ConfigurationTreeParams params);

}
