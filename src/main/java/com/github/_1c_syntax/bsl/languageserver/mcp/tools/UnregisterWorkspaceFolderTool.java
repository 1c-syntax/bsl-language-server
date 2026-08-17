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
import com.github._1c_syntax.bsl.languageserver.mcp.dto.WorkspaceFolderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * MCP-инструмент {@code unregister_workspace_folder}: убирает ранее зарегистрированную рабочую
 * папку из общего контекста сервера и освобождает её индекс. Файлы на диске не трогаются.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class UnregisterWorkspaceFolderTool {

  private final McpWorkspaceResolver workspaceResolver;
  private final McpWorkspaceBootstrap workspaceBootstrap;
  private final ServerContextProvider serverContextProvider;

  /**
   * Результат удаления.
   *
   * @param workspaceFolder Рабочая папка, регистрация которой снята.
   * @param stillDeclaredByRoots {@code true}, если папка осталась в контексте сервера: её
   *   продолжает удерживать корень, объявленный клиентом через MCP roots.
   * @param remaining Рабочие папки, оставшиеся зарегистрированными.
   */
  public record Result(
    WorkspaceFolderDto workspaceFolder,
    boolean stillDeclaredByRoots,
    List<WorkspaceFolderDto> remaining
  ) {
  }

  /**
   * Снять регистрацию рабочей папки.
   * <p>
   * Побочный эффект: папка удаляется из общего контекста сервера вместе с собранным индексом;
   * файлы на диске не изменяются. Папка, которую сверх этого удерживает корень, объявленный
   * клиентом через MCP roots, остаётся в контексте.
   *
   * @param workspaceFolder URI зарегистрированной рабочей папки (либо путь к ней).
   * @return Рабочая папка, признак того, что она осталась в контексте, и оставшиеся
   *   зарегистрированными папки.
   * @throws IllegalArgumentException Если значение не совпало ни с одной зарегистрированной рабочей
   *   папкой, за ним не стоит каталог (синтетическая рабочая область LSP-клиента) либо папку
   *   добавили не через MCP — папку редактора инструмент забирать не вправе.
   * @throws IllegalStateException Если папку не удалось адресовать для удаления (её каталога уже
   *   нет на диске) — папка остаётся зарегистрированной, о чём и сообщает исключение.
   */
  @McpTool(
    name = "unregister_workspace_folder",
    description = """
      Remove a previously registered workspace folder from this server and release its index. Pass \
      the `uri` reported by `list_workspace_folders`.
      Only server-side state is dropped — no file on disk is touched. Afterwards the other BSL \
      tools stop answering for that folder until it is registered again with \
      `register_workspace_folder`.
      A folder the client also declares through MCP roots stays indexed and keeps answering; the \
      result reports that as `stillDeclaredByRoots`. A folder that came from the editor over LSP \
      is not yours to drop — the call fails and says so.""",
    // Output schema disabled for every tool of this server: Spring AI generates a schema the results
    // then fail validation against (spring-ai#4825, #4487 — both still open as of 2.0.0). Structured
    // results are still returned, just unvalidated.
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
  public Result unregisterWorkspaceFolder(
    @McpToolParam(required = true, description = McpToolParams.WORKSPACE_FOLDER)
    String workspaceFolder
  ) {
    var workspaceUri = workspaceResolver.resolveWorkspaceFolderUri(workspaceFolder);
    if (!"file".equalsIgnoreCase(workspaceUri.getScheme())) {
      // Синтетическая рабочая область LSP-клиента (одиночный файл, untitled-буфер):
      // каталога за ней нет, снимать нечего.
      throw new IllegalArgumentException("Workspace folder `" + workspaceUri
        + "` is not backed by a directory and cannot be unregistered.");
    }
    // Описание собираем до снятия регистрации: вместе с ней уходит и имя папки из реестра.
    var folder = WorkspaceFolderDto.from(workspaceUri);
    // Папку адресуем ровно тем URI, под которым она зарегистрирована: обратный путь через Path
    // менял бы идентичность, если каталог уже удалён с диска.
    var removed = workspaceBootstrap.unregister(workspaceUri);

    // Снимок живого представления: см. ListWorkspaceFoldersTool.
    var remaining = Map.copyOf(serverContextProvider.getAllContexts()).keySet().stream()
      .flatMap(uri -> WorkspaceFolderDto.fromSnapshot(uri).stream())
      .sorted(Comparator.comparing(workspaceFolderDto -> workspaceFolderDto.uri().toString()))
      .toList();
    return new Result(folder, !removed, remaining);
  }
}
