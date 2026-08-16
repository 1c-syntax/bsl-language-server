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
package com.github._1c_syntax.bsl.languageserver.mcp.tools;

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceBootstrap;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceResolver;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.WorkspaceDto;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * MCP-инструмент {@code unregister_workspace}: убирает ранее зарегистрированное рабочее
 * пространство из общего контекста сервера и освобождает его индекс. Файлы на диске не трогаются.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class UnregisterWorkspaceTool {

  private final McpWorkspaceResolver workspaceResolver;
  private final McpWorkspaceBootstrap workspaceBootstrap;
  private final ServerContextProvider serverContextProvider;

  /**
   * Результат удаления.
   *
   * @param root Корень удалённого рабочего пространства.
   * @param remaining Рабочие пространства, оставшиеся зарегистрированными.
   */
  public record Result(String root, List<WorkspaceDto> remaining) {
  }

  @McpTool(
    name = "unregister_workspace",
    description = "Remove a previously registered workspace from this server and release its index. "
      + "Pass the `root` reported by `list_workspaces`. Only server-side state is dropped — no file on "
      + "disk is touched. Afterwards the other BSL tools stop answering for that project until it is "
      + "registered again with `register_workspace`.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO fields
    // (here — the path of a workspace without a folder). Known upstream bug, open as of 2.0.0-M6.
    generateOutputSchema = false,
    // The only destructive tool of this server: per the spec destructiveHint = false means "additive
    // updates only", and dropping a registration throws away an index that took minutes to build on
    // a large configuration. The sources themselves are never touched. A repeated call no longer
    // changes anything (it fails with an explicit error), hence idempotent. Same shape as
    // `delete_entities` of the reference memory server.
    annotations = @McpTool.McpAnnotations(
      readOnlyHint = false,
      destructiveHint = true,
      idempotentHint = true,
      openWorldHint = false))
  public Result unregisterWorkspace(
    @McpToolParam(required = true, description = McpToolParams.WORKSPACE_ROOT)
    String root
  ) {
    var workspaceUri = workspaceResolver.resolveWorkspaceUri(root);
    if (!"file".equalsIgnoreCase(workspaceUri.getScheme())) {
      // Синтетическое рабочее пространство LSP-клиента (одиночный файл, untitled-буфер):
      // каталога за ним нет, снимать нечего.
      throw new IllegalArgumentException("Workspace `" + workspaceUri
        + "` is not backed by a directory and cannot be unregistered.");
    }
    workspaceBootstrap.remove(Absolute.path(workspaceUri));

    var remaining = serverContextProvider.getAllContexts().entrySet().stream()
      .map(entry -> WorkspaceDto.from(entry.getKey(), entry.getValue()))
      .sorted(Comparator.comparing(WorkspaceDto::root))
      .toList();
    return new Result(workspaceUri.toString(), remaining);
  }
}
