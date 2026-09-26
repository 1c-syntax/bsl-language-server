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
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Адресация рабочих папок в MCP: приведение значения, присланного клиентом, к URI
 * зарегистрированной рабочей папки и общий текст подсказки о том, как папку зарегистрировать.
 * <p>
 * Рабочая папка здесь — то же, что workspace folder в LSP: один корневой каталог проекта.
 * Множество зарегистрированных папок и составляет рабочую область (workspace), которую
 * обслуживает сервер.
 * <p>
 * Подсказка — единственный текст о незарегистрированной папке во всём MCP-слое: свой вариант
 * писать нельзя, иначе сообщения разойдутся между собой.
 */
@Slf4j
public final class McpWorkspaceFolders {

  /**
   * Имя MCP-инструмента, который регистрирует и индексирует каталог как рабочую папку.
   */
  public static final String REGISTER_TOOL = "register_workspace_folder";

  /**
   * Имя MCP-инструмента, который перечисляет зарегистрированные рабочие папки.
   */
  public static final String LIST_TOOL = "list_workspace_folders";

  private static final String FILE_SCHEME_PREFIX = "file://";

  private McpWorkspaceFolders() {
  }

  /**
   * Привести рабочую папку, присланную клиентом, к URI в том же виде, в котором папки
   * регистрируются в {@code ServerContextProvider}.
   * <p>
   * Принимается и URI ({@code file:///C:/repo}), и обычный путь файловой системы
   * ({@code C:\repo}, {@code /home/user/repo}, {@code ./repo}) — MCP-клиенты присылают и то, и другое.
   *
   * @param rawFolder Рабочая папка в виде URI либо пути файловой системы.
   * @return Нормализованный URI рабочей папки.
   * @throws IllegalArgumentException Если значение не удаётся разобрать ни как URI, ни как путь.
   */
  public static URI toWorkspaceFolderUri(String rawFolder) {
    var trimmed = normalizeWindowsFileUri(rawFolder.trim());
    try {
      if (hasUriScheme(trimmed)) {
        return Absolute.uri(trimmed);
      }
      return Absolute.path(trimmed).toUri();
    } catch (Exception e) {
      // Ловится Exception, а не RuntimeException: приведение к каноническому виду обращается
      // к диску, и Absolute прокидывает оттуда IOException, не объявляя его. Поймать его отдельно
      // нельзя — компилятор такого перехвата не примет, — а без него клиент вместо
      // самодостаточного объяснения получил бы сырую ошибку ввода-вывода.
      throw new IllegalArgumentException(
        "Unsupported workspace folder `" + rawFolder
          + "`: expected an absolute directory path or a file: URI.", e);
    }
  }

  /**
   * Текст, объясняющий клиенту, какие рабочие папки доступны и что делать, если нужной нет.
   *
   * @param registeredFolders URI зарегистрированных рабочих папок.
   * @return Подсказка для добавления к сообщению об ошибке или к ответу инструмента.
   */
  public static String registrationHint(Collection<URI> registeredFolders) {
    if (registeredFolders.isEmpty()) {
      return "No workspace folder is registered on this server yet, so no 1C/OneScript sources are "
        + "indexed. Call the `" + REGISTER_TOOL + "` tool with the project root directory an editor "
        + "would open as a workspace folder — the one holding the sources and, when present, "
        + "`.bsl-language-server.json` — and retry with the `uri` it returns.";
    }
    return "Registered workspace folders: "
      + registeredFolders.stream().map(URI::toString).sorted().collect(Collectors.joining(", "))
      + ". Pass one of them, or call the `" + REGISTER_TOOL + "` tool to add another project directory. "
      + "The `" + LIST_TOOL + "` tool always returns the current list.";
  }

  /**
   * Починить {@code file://}-URI в windows-патологии, которую шлют некоторые MCP-клиенты:
   * {@code file://D:\path\with\backslashes} вместо RFC 8089 {@code file:///D:/path}.
   * <p>
   * Покрытые случаи: {@code file://<letter>:<path>} (буква диска как «host» — {@code Absolute.uri}
   * съел бы двоеточие и получил хост {@code D}) и backslash'и в path-части. Оба приводятся к
   * {@code file:///<letter>:/<path-with-forward-slashes>}. Значение без схемы (обычный
   * windows-путь) не меняется: его разбирает уже файловая система.
   *
   * @param raw Значение, присланное клиентом.
   * @return Нормализованный URI, либо исходное значение, если нормализация не требуется.
   */
  static String normalizeWindowsFileUri(String raw) {
    if (raw.isEmpty()) {
      return raw;
    }
    var result = raw;
    if (startsWithIgnoreCase(result, FILE_SCHEME_PREFIX)
      && hasDriveLetterAfterPrefix(result, FILE_SCHEME_PREFIX.length())) {
      result = "file:///" + result.substring(FILE_SCHEME_PREFIX.length());
    }
    var schemeDelimiter = result.indexOf("://");
    if (schemeDelimiter >= 0 && result.indexOf('\\', schemeDelimiter) >= 0) {
      var head = result.substring(0, schemeDelimiter + "://".length());
      var tail = result.substring(schemeDelimiter + "://".length()).replace('\\', '/');
      result = head + tail;
    }
    return result;
  }

  private static boolean startsWithIgnoreCase(String value, String prefix) {
    return value.regionMatches(true, 0, prefix, 0, prefix.length());
  }

  private static boolean hasDriveLetterAfterPrefix(String value, int offset) {
    return value.length() > offset + 1
      && Character.isLetter(value.charAt(offset))
      && value.charAt(offset + 1) == ':';
  }

  // Путь и URI различаем по схеме, но букву диска (`C:/repo`) схемой не считаем: она однобуквенная,
  // а реальные схемы (file, bsl-language-server) — длиннее.
  private static boolean hasUriScheme(String value) {
    try {
      var scheme = new URI(value).getScheme();
      return scheme != null && scheme.length() > 1;
    } catch (URISyntaxException e) {
      // Не URI (например, windows-путь с обратными слэшами) — значит, путь.
      LOGGER.debug("Workspace folder `{}` is not a URI, treating it as a path", value, e);
      return false;
    }
  }
}
