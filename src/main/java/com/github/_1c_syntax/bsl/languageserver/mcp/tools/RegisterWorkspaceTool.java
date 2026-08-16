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
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaces;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.WorkspaceDto;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MCP-инструмент {@code register_workspace}: регистрирует каталог рабочей области (аналог LSP
 * workspace folder — корень проекта, а не подкаталог с исходниками: конфигурационный файл
 * {@code .bsl-language-server.json} читается только из корня) и индексирует его исходники
 * 1С/OneScript, после чего файлы каталога становятся видны остальным инструментам, а его корень —
 * допустимым значением параметра {@code root}.
 * <p>
 * Повторная регистрация уже зарегистрированного каталога переиндексацию не запускает: возвращается
 * текущее состояние с признаком {@code alreadyRegistered}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class RegisterWorkspaceTool {

  private final McpWorkspaceBootstrap workspaceBootstrap;
  private final ServerContextProvider serverContextProvider;

  /**
   * Результат регистрации.
   *
   * @param workspace Зарегистрированное рабочее пространство: его {@code root} нужно передавать
   *   в остальные инструменты.
   * @param alreadyRegistered {@code true}, если каталог был зарегистрирован ранее и повторная
   *   индексация не выполнялась.
   */
  public record Result(WorkspaceDto workspace, boolean alreadyRegistered) {
  }

  /**
   * Зарегистрировать каталог как рабочее пространство.
   * <p>
   * Побочный эффект: для нового каталога выполняется индексация исходников — операция может быть
   * длительной. Уже зарегистрированный каталог не переиндексируется, возвращается его текущее
   * состояние.
   *
   * @param path Каталог рабочей области (workspace folder): абсолютный или относительный путь
   *   либо {@code file:}-URI.
   * @return Рабочее пространство и признак того, что оно было зарегистрировано ранее.
   * @throws IllegalArgumentException Если путь пуст, не указывает на локальный каталог,
   *   не существует либо является файлом.
   */
  @McpTool(
    name = "register_workspace",
    description = "Register a workspace folder and index its 1C:Enterprise (BSL) and OneScript sources, "
      + "so that the other BSL tools can analyse it. Required before analysing any project the "
      + "`list_workspaces` tool does not already report. Pass the project root directory — the same "
      + "folder an editor opens as an LSP workspace folder — not a sources subfolder and not a single "
      + "file: `.bsl-language-server.json` is only read from the workspace root. Indexing a large "
      + "configuration may take a while; registering an already registered directory returns "
      + "immediately without re-indexing. Returns the `root` to pass to the other tools. Nothing on "
      + "disk is modified.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO fields
    // (here — the path of a workspace without a folder). Known upstream bug, open as of 2.0.0-M6.
    generateOutputSchema = false,
    // Mutates server state only (the indexed workspace set) — never the analysed sources, so the
    // update is additive rather than destructive. Repeated calls for the same directory are no-ops,
    // hence idempotent. Same shape as `create_entities` of the reference memory server.
    annotations = @McpTool.McpAnnotations(
      readOnlyHint = false,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false))
  public Result registerWorkspace(
    @McpToolParam(required = true, description = McpToolParams.WORKSPACE_PATH)
    String path
  ) {
    var srcDir = toSourceDirectory(path);
    var workspaceUri = srcDir.toUri();

    var existing = serverContextProvider.getAllContexts().get(workspaceUri);
    if (existing != null) {
      return new Result(WorkspaceDto.from(workspaceUri, existing), true);
    }

    workspaceBootstrap.index(srcDir);

    var registered = serverContextProvider.getAllContexts().get(workspaceUri);
    if (registered == null) {
      throw new IllegalStateException("Workspace was indexed but is not registered: " + workspaceUri);
    }
    return new Result(WorkspaceDto.from(workspaceUri, registered), false);
  }

  private static Path toSourceDirectory(String rawPath) {
    if (rawPath.isBlank()) {
      throw new IllegalArgumentException(
        "Workspace path is required: pass the project root directory to index.");
    }
    var uri = McpWorkspaces.toWorkspaceUri(rawPath);
    if (!"file".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException(
        "Workspace path must point to a local directory, got: " + rawPath);
    }

    var srcDir = Absolute.path(uri);
    if (Files.isDirectory(srcDir)) {
      return srcDir;
    }
    if (Files.exists(srcDir)) {
      throw new IllegalArgumentException("Workspace path is a file, not a directory: " + srcDir
        + ". Pass the workspace folder — the project root directory that holds the sources.");
    }
    throw new IllegalArgumentException("Workspace path does not exist: " + srcDir
      + ". Pass an existing workspace folder; relative paths are resolved against the "
      + "server working directory, so prefer an absolute path.");
  }
}
