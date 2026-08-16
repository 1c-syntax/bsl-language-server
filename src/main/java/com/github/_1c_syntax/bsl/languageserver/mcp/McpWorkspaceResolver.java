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

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Выбор рабочей папки для MCP-инструментов, у которых нет явной привязки к конкретному
 * файлу (например, {@code type_info}, {@code global_member_info}). Клиент обязан явно
 * указать {@code root} (одно из значений, которые вернул {@code list_workspace_folders}), потому что
 * ответ может различаться между зарегистрированными папками (конфигурации, OneScript-проекты,
 * библиотеки).
 * <p>
 * Сравнение URI ведётся через {@link McpWorkspaceFolders#toWorkspaceFolderUri(String)} — чтобы
 * клиентское представление ({@code file://D:/repo} / {@code file:///D:/repo/} / голый путь
 * {@code D:\repo}) сходилось с тем URI, под которым папка зарегистрирована.
 * <p>
 * Инвариант ошибки: если корень не передан или не совпал ни с одной рабочей папкой,
 * сообщение перечисляет зарегистрированные корни и называет инструмент их регистрации
 * (см. {@link McpWorkspaceFolders#registrationHint}).
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class McpWorkspaceResolver {

  private final ServerContextProvider serverContextProvider;

  /**
   * Выбрать рабочую папку для tool-запроса.
   *
   * @param requestedRoot Корень рабочей папки (URI либо путь), на который ссылается запрос.
   * @return URI зарегистрированной рабочей папки.
   * @throws IllegalArgumentException если {@code requestedRoot} пуст/отсутствует, либо не
   *   совпадает ни с одной зарегистрированной рабочей папкой. Сообщение содержит список
   *   зарегистрированных корней и указание на {@code register_workspace_folder}.
   */
  public URI resolveWorkspaceUri(@Nullable String requestedRoot) {
    var registeredRoots = serverContextProvider.getAllContexts().keySet();
    if (requestedRoot == null || requestedRoot.isBlank()) {
      throw new IllegalArgumentException(
        "Workspace folder root is required: every folder-scoped BSL tool must say which workspace "
          + "folder to answer for. " + McpWorkspaceFolders.registrationHint(registeredRoots));
    }
    var normalized = McpWorkspaceFolders.toWorkspaceFolderUri(requestedRoot);
    return registeredRoots.stream()
      .filter(uri -> uri.equals(normalized))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException(
        "No registered workspace folder matches root: " + requestedRoot + ". "
          + McpWorkspaceFolders.registrationHint(registeredRoots)));
  }
}
