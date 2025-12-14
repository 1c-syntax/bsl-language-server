/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Провайдер контекстов сервера для мульти-workspace окружения.
 * <p>
 * Управляет коллекцией экземпляров {@link ServerContext} (по одному на каждую workspace folder)
 * и обеспечивает маршрутизацию от URI документа к контексту сервера.
 * <p>
 * Для обратной совместимости использует singleton {@link ServerContext},
 * когда workspace folders не настроены.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerContextProvider {

  private final ObjectProvider<ServerContext> serverContextProvider;
  private final LanguageServerConfiguration configuration;

  private final Map<URI, ServerContext> contexts = new ConcurrentHashMap<>();
  private final Map<URI, Path> workspaceRoots = new ConcurrentHashMap<>();

  /**
   * Добавить workspace folder и создать для нее контекст сервера.
   *
   * @param workspaceFolder информация о workspace folder
   * @return созданный контекст сервера
   */
  public ServerContext addWorkspace(WorkspaceFolder workspaceFolder) {
    var uri = Absolute.uri(workspaceFolder.getUri());
    
    if (contexts.containsKey(uri)) {
      LOGGER.debug("Workspace {} already exists", uri);
      return contexts.get(uri);
    }

    Path rootPath;
    try {
      rootPath = new File(Absolute.uri(workspaceFolder.getUri()).getPath()).getCanonicalFile().toPath();
    } catch (IOException e) {
      LOGGER.error("Can't read root URI from workspace folder: {}", workspaceFolder.getUri(), e);
      throw new IllegalArgumentException("Invalid workspace folder URI", e);
    }

    // Create new ServerContext instance for workspace using Spring
    var serverContext = serverContextProvider.getObject();
    var configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, rootPath);
    serverContext.setConfigurationRoot(configurationRoot);

    contexts.put(uri, serverContext);
    workspaceRoots.put(uri, rootPath);

    LOGGER.info("Added workspace: {} at {}", workspaceFolder.getName(), rootPath);
    return serverContext;
  }

  /**
   * Удалить workspace folder и очистить ее контекст сервера.
   *
   * @param workspaceFolder информация о workspace folder
   */
  public void removeWorkspace(WorkspaceFolder workspaceFolder) {
    var uri = URI.create(workspaceFolder.getUri());
    var serverContext = contexts.remove(uri);
    workspaceRoots.remove(uri);
    
    if (serverContext != null) {
      serverContext.clear();
      LOGGER.info("Removed workspace: {}", workspaceFolder.getName());
    }
  }

  /**
   * Получить контекст сервера для URI документа.
   * <p>
   * Основной метод для маршрутизации документа к контексту. Находит соответствующий
   * контекст сервера на основе пути документа.
   *
   * @param documentUri URI документа
   * @return контекст сервера, содержащий документ, или пустой Optional, если не найден
   */
  public Optional<ServerContext> getServerContext(URI documentUri) {
    if (!"file".equalsIgnoreCase(documentUri.getScheme())) {
      return Optional.empty();
    }

    try {
      var documentPath = Path.of(documentUri);
      return workspaceRoots.entrySet().stream()
        .filter(entry -> documentPath.startsWith(entry.getValue()))
        .map(entry -> contexts.get(entry.getKey()))
        .findFirst();
    } catch (UnsupportedOperationException e) {
      return Optional.empty();
    }
  }

  /**
   * Получить все контексты серверов.
   *
   * @return неизменяемая коллекция всех контекстов серверов
   */
  public Collection<ServerContext> getAllContexts() {
    return Collections.unmodifiableCollection(contexts.values());
  }

  /**
   * Очистить все workspaces.
   */
  public void clear() {
    contexts.values().forEach(ServerContext::clear);
    contexts.clear();
    workspaceRoots.clear();
    LOGGER.info("Cleared all workspaces");
  }

  /**
   * Проверить, есть ли зарегистрированные workspaces.
   *
   * @return true, если зарегистрирован хотя бы один workspace
   */
  public boolean hasWorkspaces() {
    return !contexts.isEmpty();
  }
}
