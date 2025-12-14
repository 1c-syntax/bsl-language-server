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
 * Provider for server contexts in multi-workspace environment.
 * <p>
 * Maintains a collection of {@link ServerContext} instances (one per workspace folder)
 * and provides document URI to server context routing.
 * <p>
 * For backward compatibility, falls back to the singleton {@link ServerContext}
 * when no workspace folders are configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerContextProvider {

  private final ObjectProvider<DocumentContext> documentContextProvider;
  private final WorkDoneProgressHelper workDoneProgressHelper;
  private final LanguageServerConfiguration configuration;

  private final Map<URI, ServerContext> contexts = new ConcurrentHashMap<>();
  private final Map<URI, Path> workspaceRoots = new ConcurrentHashMap<>();

  /**
   * Add workspace folder and create server context for it.
   *
   * @param workspaceFolder workspace folder information
   * @return created server context
   */
  public ServerContext addWorkspace(WorkspaceFolder workspaceFolder) {
    var uri = URI.create(workspaceFolder.getUri());
    
    if (contexts.containsKey(uri)) {
      LOGGER.debug("Workspace {} already exists", uri);
      return contexts.get(uri);
    }

    Path rootPath;
    try {
      rootPath = new File(new URI(workspaceFolder.getUri()).getPath()).getCanonicalFile().toPath();
    } catch (URISyntaxException | IOException e) {
      LOGGER.error("Can't read root URI from workspace folder: {}", workspaceFolder.getUri(), e);
      throw new IllegalArgumentException("Invalid workspace folder URI", e);
    }

    // Create new ServerContext instance for workspace
    var serverContext = new ServerContext(documentContextProvider, workDoneProgressHelper, configuration);
    var configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, rootPath);
    serverContext.setConfigurationRoot(configurationRoot);

    contexts.put(uri, serverContext);
    workspaceRoots.put(uri, rootPath);

    LOGGER.info("Added workspace: {} at {}", workspaceFolder.getName(), rootPath);
    return serverContext;
  }

  /**
   * Remove workspace folder and clear its server context.
   *
   * @param workspaceFolder workspace folder information
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
   * Get server context for document URI.
   * <p>
   * Main method for document-to-context routing. Finds the appropriate
   * server context based on document path.
   *
   * @param documentUri document URI
   * @return server context containing the document, or empty if not found
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
   * Get all server contexts.
   *
   * @return unmodifiable collection of all server contexts
   */
  public Collection<ServerContext> getAllContexts() {
    return Collections.unmodifiableCollection(contexts.values());
  }

  /**
   * Clear all workspaces.
   */
  public void clear() {
    contexts.values().forEach(ServerContext::clear);
    contexts.clear();
    workspaceRoots.clear();
    LOGGER.info("Cleared all workspaces");
  }

  /**
   * Check if there are any registered workspaces.
   *
   * @return true if at least one workspace is registered
   */
  public boolean hasWorkspaces() {
    return !contexts.isEmpty();
  }
}
