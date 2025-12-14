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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер контекстов рабочих областей.
 * <p>
 * Управляет коллекцией контекстов для различных рабочих областей (workspaces).
 * Обеспечивает создание, получение и удаление контекстов рабочих областей,
 * а также маршрутизацию документов к соответствующим рабочим областям.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceContextManager {

  private final ObjectProvider<DocumentContext> documentContextProvider;
  private final WorkDoneProgressHelper workDoneProgressHelper;
  private final LanguageServerConfiguration configuration;

  private final Map<URI, WorkspaceContext> workspaces = new ConcurrentHashMap<>();

  /**
   * Добавить рабочую область.
   *
   * @param workspaceFolder информация о рабочей области
   * @return контекст созданной рабочей области
   */
  public WorkspaceContext addWorkspace(WorkspaceFolder workspaceFolder) {
    var uri = URI.create(workspaceFolder.getUri());
    
    if (workspaces.containsKey(uri)) {
      LOGGER.debug("Workspace {} already exists", uri);
      return workspaces.get(uri);
    }

    Path rootPath;
    try {
      rootPath = new File(new URI(workspaceFolder.getUri()).getPath()).getCanonicalFile().toPath();
    } catch (URISyntaxException | IOException e) {
      LOGGER.error("Can't read root URI from workspace folder: {}", workspaceFolder.getUri(), e);
      throw new IllegalArgumentException("Invalid workspace folder URI", e);
    }

    // Создаем новый экземпляр ServerContext для workspace
    var serverContext = new ServerContext(documentContextProvider, workDoneProgressHelper, configuration);
    var configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, rootPath);
    serverContext.setConfigurationRoot(configurationRoot);

    var workspaceContext = new WorkspaceContext(workspaceFolder, serverContext, rootPath);
    workspaces.put(uri, workspaceContext);

    LOGGER.info("Added workspace: {} at {}", workspaceFolder.getName(), rootPath);
    return workspaceContext;
  }

  /**
   * Удалить рабочую область.
   *
   * @param workspaceFolder информация о рабочей области
   */
  public void removeWorkspace(WorkspaceFolder workspaceFolder) {
    var uri = URI.create(workspaceFolder.getUri());
    var workspaceContext = workspaces.remove(uri);
    
    if (workspaceContext != null) {
      workspaceContext.getServerContext().clear();
      LOGGER.info("Removed workspace: {}", workspaceFolder.getName());
    }
  }

  /**
   * Получить контекст рабочей области по URI.
   *
   * @param uri URI рабочей области
   * @return контекст рабочей области или null
   */
  @Nullable
  public WorkspaceContext getWorkspace(URI uri) {
    return workspaces.get(uri);
  }

  /**
   * Найти рабочую область, содержащую документ.
   *
   * @param documentUri URI документа
   * @return контекст рабочей области или пустой Optional
   */
  public Optional<WorkspaceContext> findWorkspaceForDocument(URI documentUri) {
    return workspaces.values().stream()
      .filter(workspace -> workspace.contains(documentUri))
      .findFirst();
  }

  /**
   * Получить все рабочие области.
   *
   * @return неизменяемая коллекция всех рабочих областей
   */
  public Collection<WorkspaceContext> getAllWorkspaces() {
    return Collections.unmodifiableCollection(workspaces.values());
  }

  /**
   * Очистить все рабочие области.
   */
  public void clear() {
    workspaces.values().forEach(workspace -> workspace.getServerContext().clear());
    workspaces.clear();
    LOGGER.info("Cleared all workspaces");
  }

  /**
   * Проверить, есть ли зарегистрированные рабочие области.
   *
   * @return true, если есть хотя бы одна рабочая область
   */
  public boolean hasWorkspaces() {
    return !workspaces.isEmpty();
  }
}
