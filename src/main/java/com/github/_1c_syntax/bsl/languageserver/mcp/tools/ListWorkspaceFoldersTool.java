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
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceFolders;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.WorkspaceFolderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * MCP-инструмент {@code list_workspace_folders}: перечисляет рабочие папки (workspace folders
 * в терминах LSP), зарегистрированные и проиндексированные сервером, с их {@code uri} —
 * значением, которое остальные инструменты принимают в параметре {@code workspaceFolder}.
 * <p>
 * Пока не зарегистрирована ни одна папка, ни один зависящий от неё инструмент работать не может,
 * поэтому в ответе есть поле {@code hint} — текстовое описание следующего шага, в том числе
 * когда список пуст.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class ListWorkspaceFoldersTool {

  private final ServerContextProvider serverContextProvider;

  /**
   * Список рабочих папок.
   *
   * @param workspaceFolders Зарегистрированные рабочие папки, упорядоченные по {@code uri}.
   * @param hint Что делать дальше: какие папки зарегистрированы и как зарегистрировать недостающую.
   */
  public record Result(List<WorkspaceFolderDto> workspaceFolders, String hint) {
  }

  @McpTool(
    name = "list_workspace_folders",
    description = """
      List the workspace folders (1C:Enterprise configurations and OneScript projects) currently \
      registered and indexed by this server, together with the `uri` the other BSL tools expect in \
      their `workspaceFolder` argument. Together these folders make up the workspace this server serves, the same way an LSP \
      client's workspace is made up of workspace folders.
      Start here: every other BSL tool answers only inside a registered folder, and file paths \
      outside every registered folder are rejected. An empty list means nothing is indexed yet — \
      register the project directory with `register_workspace_folder` first.""",
    // Output schema disabled for every tool of this server: Spring AI generates a schema the results
    // then fail validation against (spring-ai#4825, #4487 — both still open as of 2.0.0). Structured
    // results are still returned, just unvalidated.
    generateOutputSchema = false,
    // Read-only: only reports server state, never mutates anything. Hint clients so the tool is not
    // treated as destructive.
    annotations = @McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false))
  public Result listWorkspaceFolders() {
    // Снимок: getAllContexts отдаёт живое представление, а список и подсказка обходят его порознь —
    // без копии параллельная регистрация попала бы в один из них и не попала в другой.
    var contexts = Map.copyOf(serverContextProvider.getAllContexts());
    var workspaceFolders = contexts.keySet().stream()
      .flatMap(uri -> WorkspaceFolderDto.fromSnapshot(uri).stream())
      .sorted(Comparator.comparing(workspaceFolder -> workspaceFolder.uri().toString()))
      .toList();
    return new Result(workspaceFolders, McpWorkspaceFolders.registrationHint(contexts.keySet()));
  }
}
