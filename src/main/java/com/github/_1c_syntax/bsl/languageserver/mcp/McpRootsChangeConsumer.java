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

import com.github._1c_syntax.utils.Absolute;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Приводит набор рабочих папок к корням (roots), объявленным клиентом — прямому аналогу
 * workspace folders в LSP ({@code workspace/didChangeWorkspaceFolders}).
 * <p>
 * Бин подхватывается автоконфигурацией Spring AI как обработчик изменения roots. Разницу
 * с текущим набором и само владение папками считает {@link McpWorkspaceBootstrap}: своей копии
 * состояния здесь нет, иначе она разошлась бы с фактическим набором папок.
 */
@Slf4j
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class McpRootsChangeConsumer implements BiConsumer<McpSyncServerExchange, List<Root>> {

  private final McpWorkspaceBootstrap workspaceBootstrap;

  @Override
  public void accept(McpSyncServerExchange exchange, List<Root> roots) {
    var declared = roots.stream()
      .map(McpRootsChangeConsumer::toPath)
      .filter(Objects::nonNull)
      .toList();

    workspaceBootstrap.syncRoots(declared);
  }

  private static @Nullable Path toPath(Root root) {
    var raw = root.uri();
    try {
      return Absolute.path(McpWorkspaceFolders.toWorkspaceFolderUri(raw));
    } catch (RuntimeException e) {
      LOGGER.warn("Skipping unsupported MCP root uri `{}`", raw, e);
      return null;
    }
  }
}
