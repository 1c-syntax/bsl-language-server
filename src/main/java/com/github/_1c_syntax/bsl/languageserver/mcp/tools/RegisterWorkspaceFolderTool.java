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
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceFolders;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.WorkspaceFolderDto;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MCP-инструмент {@code register_workspace_folder}: регистрирует каталог как рабочую папку
 * (workspace folder в терминах LSP — корень проекта, а не подкаталог с исходниками:
 * конфигурационный файл {@code .bsl-language-server.json} читается только из корня) и индексирует
 * её исходники 1С/OneScript, после чего файлы папки становятся видны остальным инструментам,
 * а её {@code uri} — допустимым значением параметра {@code workspaceFolder}.
 * <p>
 * Повторная регистрация уже зарегистрированного каталога переиндексацию не запускает: возвращается
 * текущее состояние с признаком {@code alreadyRegistered}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class RegisterWorkspaceFolderTool {

  private final McpWorkspaceBootstrap workspaceBootstrap;

  /**
   * Результат регистрации.
   *
   * @param workspaceFolder Зарегистрированная рабочая папка: её {@code uri} нужно передавать
   *   в остальные инструменты.
   * @param alreadyRegistered {@code true}, если каталог был зарегистрирован ранее и повторная
   *   индексация не выполнялась.
   */
  public record Result(WorkspaceFolderDto workspaceFolder, boolean alreadyRegistered) {
  }

  /**
   * Зарегистрировать каталог как рабочую папку.
   * <p>
   * Побочный эффект: для нового каталога выполняется индексация исходников — операция может быть
   * длительной. Уже зарегистрированный каталог не переиндексируется, возвращается его текущее
   * состояние.
   *
   * @param path Каталог рабочей папки: абсолютный или относительный путь либо {@code file:}-URI.
   * @param name Имя рабочей папки; {@code null} — взять из имени каталога.
   * @return Рабочая папка и признак того, что она была зарегистрирована ранее.
   * @throws IllegalArgumentException Если путь пуст, не указывает на локальный каталог,
   *   не существует либо является файлом.
   */
  @McpTool(
    name = "register_workspace_folder",
    description = """
      Add a workspace folder to this server and index its 1C:Enterprise (BSL) and OneScript \
      sources, so that the other BSL tools can analyse it. Required before analysing any project \
      the `list_workspace_folders` tool does not already report.
      Pass the project root directory — a workspace folder in the LSP sense, the folder an editor \
      opens — not a subfolder of it and not a single file: `.bsl-language-server.json` is only read \
      from the folder root.
      Indexing a large configuration may take a while; adding an already registered folder returns \
      immediately without re-indexing. Returns the `uri` to pass to the other tools as \
      `workspaceFolder`. Nothing on disk is modified.""",
    // Output schema disabled for every tool of this server: Spring AI generates a schema the results
    // then fail validation against (spring-ai#4825, #4487 — both still open as of 2.0.0). Structured
    // results are still returned, just unvalidated.
    generateOutputSchema = false,
    // Mutates server state only (the indexed workspace set) — never the analysed sources, so the
    // update is additive rather than destructive. Repeated calls for the same directory are no-ops,
    // hence idempotent. Same shape as `create_entities` of the reference memory server.
    annotations = @McpTool.McpAnnotations(
      readOnlyHint = false,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false))
  public Result registerWorkspaceFolder(
    @McpToolParam(required = true, description = McpToolParams.WORKSPACE_PATH)
    String path,
    @McpToolParam(required = false, description = McpToolParams.WORKSPACE_NAME)
    @Nullable String name
  ) {
    var srcDir = toSourceDirectory(path);
    // Проверка «уже зарегистрирован» и индексация — одна атомарная операция: иначе два
    // параллельных вызова для одного каталога оба увидели бы «не зарегистрирован» и
    // проиндексировали бы его дважды.
    var alreadyRegistered = workspaceBootstrap.register(srcDir, name);
    return new Result(WorkspaceFolderDto.from(srcDir.toUri()), alreadyRegistered);
  }

  private static Path toSourceDirectory(String rawPath) {
    if (rawPath.isBlank()) {
      throw new IllegalArgumentException(
        "Workspace path is required: pass the project root directory to index.");
    }
    var uri = McpWorkspaceFolders.toWorkspaceFolderUri(rawPath);
    if (!"file".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException(
        "Workspace path must point to a local directory, got: " + rawPath);
    }

    Path srcDir;
    try {
      srcDir = Absolute.path(uri);
    } catch (Exception e) {
      // Канонизация пути повторяется здесь уже после нормализации, и между двумя обращениями
      // к диску каталог может исчезнуть. Absolute.path прокидывает IOException из
      // getCanonicalFile(), не объявляя его, поэтому ловим Exception: иначе клиент получил бы
      // сырую ошибку ввода-вывода вместо объяснения, что передать.
      throw new IllegalArgumentException("Workspace path cannot be resolved: " + rawPath
        + ". Pass an existing directory — the project root the editor opens.", e);
    }
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
