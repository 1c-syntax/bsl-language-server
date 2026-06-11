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

import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceBootstrap;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * MCP-инструменты управления рабочими пространствами — аналог работы с workspace folders в LSP
 * ({@code workspace/didChangeWorkspaceFolders}). Позволяют агенту добавлять и удалять каталоги
 * исходников в общий {@code ServerContextProvider} во время сессии.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class WorkspaceTool {

  private final McpWorkspaceBootstrap workspaceBootstrap;

  /**
   * Результат добавления рабочего пространства.
   *
   * @param workspace Путь добавленного каталога.
   * @param indexedFiles Количество проиндексированных файлов.
   */
  public record AddResult(String workspace, int indexedFiles) {
  }

  /**
   * Результат удаления рабочего пространства.
   *
   * @param workspace Путь удалённого каталога.
   */
  public record RemoveResult(String workspace) {
  }

  @McpTool(
    name = "add_workspace",
    description = "Register a source directory as a workspace and index it into the shared context.",
    // Output schema disabled: see other MCP tools. Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    generateOutputSchema = false)
  public AddResult addWorkspace(
    @McpToolParam(required = true, description = "Path to the source directory to add.")
    String workspace
  ) {
    var srcDir = Absolute.path(workspace);
    if (!srcDir.toFile().exists()) {
      throw new IllegalArgumentException("Source directory does not exist: " + workspace);
    }
    var indexedFiles = workspaceBootstrap.index(srcDir, new File(""));
    return new AddResult(workspace, indexedFiles);
  }

  @McpTool(
    name = "remove_workspace",
    description = "Remove a previously added workspace (source directory) from the shared context.",
    // Output schema disabled: see other MCP tools. Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    generateOutputSchema = false)
  public RemoveResult removeWorkspace(
    @McpToolParam(required = true, description = "Path to the source directory to remove.")
    String workspace
  ) {
    workspaceBootstrap.remove(Absolute.path(workspace));
    return new RemoveResult(workspace);
  }
}
