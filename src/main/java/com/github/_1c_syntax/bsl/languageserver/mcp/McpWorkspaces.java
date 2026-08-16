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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Адресация рабочих пространств в MCP: приведение корня, присланного клиентом, к URI
 * зарегистрированного workspace и общий текст подсказки о том, как workspace зарегистрировать.
 * <p>
 * Подсказка выносится сюда, чтобы все сообщения о незарегистрированном workspace
 * (резолв {@code root}, доступ к файлу, ответ {@code list_workspaces}) вели клиента в одно
 * и то же место и не расходились между собой.
 */
public final class McpWorkspaces {

  /**
   * Имя MCP-инструмента, который регистрирует и индексирует каталог как рабочее пространство.
   */
  public static final String REGISTER_TOOL = "register_workspace";

  /**
   * Имя MCP-инструмента, который перечисляет зарегистрированные рабочие пространства.
   */
  public static final String LIST_TOOL = "list_workspaces";

  private McpWorkspaces() {
  }

  /**
   * Привести корень, присланный клиентом, к URI в том же виде, в котором рабочие пространства
   * регистрируются в {@code ServerContextProvider}.
   * <p>
   * Принимается и URI ({@code file:///C:/repo}), и обычный путь файловой системы
   * ({@code C:\repo}, {@code /home/user/repo}, {@code ./repo}) — MCP-клиенты присылают и то, и другое.
   *
   * @param rawRoot Корень в виде URI либо пути файловой системы.
   * @return Нормализованный URI рабочего пространства.
   * @throws IllegalArgumentException Если значение не удаётся разобрать ни как URI, ни как путь.
   */
  public static URI toWorkspaceUri(String rawRoot) {
    var trimmed = rawRoot.trim();
    try {
      if (hasUriScheme(trimmed)) {
        return Absolute.uri(trimmed);
      }
      return Absolute.path(trimmed).toUri();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
        "Unsupported workspace root `" + rawRoot + "`: expected an absolute directory path or a file: URI.", e);
    }
  }

  /**
   * Текст, объясняющий клиенту, какие рабочие пространства доступны и что делать, если нужного нет.
   *
   * @param registeredRoots Корни зарегистрированных рабочих пространств.
   * @return Подсказка для добавления к сообщению об ошибке или к ответу инструмента.
   */
  public static String registrationHint(Collection<URI> registeredRoots) {
    if (registeredRoots.isEmpty()) {
      return "No workspace is registered on this server yet, so no 1C/OneScript sources are indexed. "
        + "Call the `" + REGISTER_TOOL + "` tool with the project root directory (the folder holding "
        + "`Configuration.xml`/`src/cf` of a 1C configuration, or the OneScript sources) and retry with "
        + "the `root` it returns.";
    }
    return "Registered workspace roots: "
      + registeredRoots.stream().map(URI::toString).sorted().collect(Collectors.joining(", "))
      + ". Pass one of them, or call the `" + REGISTER_TOOL + "` tool to register another project root. "
      + "The `" + LIST_TOOL + "` tool always returns the current list.";
  }

  // Путь и URI различаем по схеме, но букву диска (`C:/repo`) схемой не считаем: она однобуквенная,
  // а реальные схемы (file, bsl-language-server) — длиннее.
  private static boolean hasUriScheme(String value) {
    try {
      var scheme = new URI(value).getScheme();
      return scheme != null && scheme.length() > 1;
    } catch (URISyntaxException e) {
      // Не URI (например, windows-путь с обратными слэшами) — значит, путь.
      return false;
    }
  }
}
