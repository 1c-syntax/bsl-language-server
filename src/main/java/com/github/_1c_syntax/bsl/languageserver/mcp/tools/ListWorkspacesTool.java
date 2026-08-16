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
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaces;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.WorkspaceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * MCP-инструмент {@code list_workspaces}: перечисляет рабочие пространства, зарегистрированные
 * и проиндексированные сервером, с корнем ({@code root}) для остальных инструментов.
 * <p>
 * Точка входа для клиента: пока рабочее пространство не зарегистрировано, ни один
 * workspace-зависимый инструмент работать не может. Поле {@code hint} в ответе описывает
 * следующий шаг — в том числе когда список пуст.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class ListWorkspacesTool {

  private final ServerContextProvider serverContextProvider;

  /**
   * Список рабочих пространств.
   *
   * @param workspaces Зарегистрированные рабочие пространства, упорядоченные по корню.
   * @param hint Что делать дальше: какие корни доступны и как зарегистрировать недостающий.
   */
  public record Result(List<WorkspaceDto> workspaces, String hint) {
  }

  @McpTool(
    name = "list_workspaces",
    description = "List the workspaces (1C:Enterprise configurations and OneScript projects) currently "
      + "registered and indexed by this server, together with the `root` value the other BSL tools "
      + "expect. Start here: every other BSL tool answers only inside a registered workspace, and file "
      + "paths outside every registered workspace are rejected. An empty list means nothing is indexed "
      + "yet — register the project root with `register_workspace` first.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO fields
    // (here — the path of a workspace without a folder). Known upstream bug, open as of 2.0.0-M6.
    generateOutputSchema = false,
    // Read-only: only reports server state, never mutates anything. Hint clients so the tool is not
    // treated as destructive.
    annotations = @McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false))
  public Result listWorkspaces() {
    var contexts = serverContextProvider.getAllContexts();
    var workspaces = contexts.entrySet().stream()
      .map(entry -> WorkspaceDto.from(entry.getKey(), entry.getValue()))
      .sorted(Comparator.comparing(WorkspaceDto::root))
      .toList();
    return new Result(workspaces, McpWorkspaces.registrationHint(contexts.keySet()));
  }
}
