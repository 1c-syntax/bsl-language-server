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
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Синхронизирует рабочие пространства MCP с корнями (roots), объявленными клиентом —
 * прямой аналог workspace folders в LSP ({@code workspace/didChangeWorkspaceFolders}).
 * <p>
 * Бин подхватывается автоконфигурацией Spring AI как обработчик изменения roots: при каждом
 * {@code notifications/roots/list_changed} сервер регистрирует новые корни как рабочие
 * пространства (с индексацией в общий {@code ServerContextProvider}) и удаляет исчезнувшие.
 */
@Slf4j
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class McpRootsChangeConsumer implements BiConsumer<McpSyncServerExchange, List<Root>> {

  private final McpWorkspaceBootstrap workspaceBootstrap;

  /**
   * Корни, ранее зарегистрированные как рабочие пространства (для вычисления разницы).
   */
  private final Set<Path> registeredRoots = ConcurrentHashMap.newKeySet();

  @Override
  public synchronized void accept(McpSyncServerExchange exchange, List<Root> roots) {
    var desired = roots.stream()
      .map(McpRootsChangeConsumer::toPath)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    desired.stream()
      .filter(path -> !registeredRoots.contains(path))
      .forEach(this::addWorkspace);

    registeredRoots.stream()
      .filter(path -> !desired.contains(path))
      .toList()
      .forEach(this::removeWorkspace);
  }

  private void addWorkspace(Path path) {
    try {
      var indexed = workspaceBootstrap.index(path);
      registeredRoots.add(path);
      LOGGER.info("Workspace `{}` added from MCP root ({} files)", path, indexed);
    } catch (RuntimeException e) {
      LOGGER.warn("Failed to add workspace from MCP root `{}`", path, e);
    }
  }

  private void removeWorkspace(Path path) {
    try {
      workspaceBootstrap.remove(path);
      LOGGER.info("Workspace `{}` removed (MCP root gone)", path);
    } catch (RuntimeException e) {
      LOGGER.warn("Failed to remove workspace from MCP root `{}`", path, e);
    } finally {
      // Снимаем отметку в любом случае, иначе состояние разойдётся с фактическим набором roots.
      registeredRoots.remove(path);
    }
  }

  private static @Nullable Path toPath(Root root) {
    var raw = root.uri();
    var normalized = normalizeWindowsFileUri(raw);
    try {
      return Absolute.path(Absolute.uri(normalized));
    } catch (RuntimeException e) {
      LOGGER.warn("Skipping unsupported MCP root uri `{}`", raw, e);
      return null;
    }
  }

  /**
   * Чинит {@code file://}-URI в windows-патологии, которую шлют некоторые MCP-клиенты:
   * {@code file://D:\path\with\backslashes} вместо RFC 8089 {@code file:///D:/path}.
   * <p>
   * Конкретно покрытые случаи: {@code file://<letter>:<path>} (буква диска как «host») и
   * любые backslash'и в path-части — приводятся к {@code file:///<letter>:/<path-with-forward-slashes>}.
   *
   * @param raw исходное значение {@code uri} из MCP-Root.
   * @return нормализованный URI, либо исходный, если нормализация не требуется.
   */
  static String normalizeWindowsFileUri(@Nullable String raw) {
    if (raw == null || raw.isEmpty()) {
      return raw;
    }
    var result = raw;
    if (startsWithIgnoreCase(result, "file://") && hasDriveLetterAfterPrefix(result, "file://".length())) {
      result = "file:///" + result.substring("file://".length());
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
}
