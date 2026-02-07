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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfigurationFactory;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentAddedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticInfosFactory;
import com.github._1c_syntax.bsl.languageserver.references.model.ReferenceContext;
import com.github._1c_syntax.utils.Absolute;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
 */
@Slf4j
@Component
public class ServerContextProvider {

  private final ObjectProvider<ServerContext> serverContextProvider;
  private final LanguageServerConfigurationFactory configurationFactory;
  private final DiagnosticInfosFactory diagnosticInfosFactory;

  private final Map<URI, ServerContext> contexts = new ConcurrentHashMap<>();
  private final Map<URI, Path> workspaceRoots = new ConcurrentHashMap<>();
  private final Map<URI, ServerContext> documentIndex = new ConcurrentHashMap<>();

  public ServerContextProvider(
    ObjectProvider<ServerContext> serverContextProvider,
    LanguageServerConfigurationFactory configurationFactory,
    DiagnosticInfosFactory diagnosticInfosFactory
  ) {
    this.serverContextProvider = serverContextProvider;
    this.configurationFactory = configurationFactory;
    this.diagnosticInfosFactory = diagnosticInfosFactory;
  }

  /**
   * Добавить workspace folder и создать для нее контекст сервера.
   *
   * @param workspaceFolder информация о workspace folder
   * @return созданный контекст сервера
   */
  public ServerContext addWorkspace(WorkspaceFolder workspaceFolder) {
    var uri = Absolute.uri(workspaceFolder.getUri());
    return addWorkspace(uri);
  }

  /**
   * Добавить workspace по URI и создать для нее контекст сервера.
   *
   * @param workspaceUri URI корня workspace
   * @return созданный контекст сервера
   */
  public ServerContext addWorkspace(URI workspaceUri) {
    var uri = Absolute.uri(workspaceUri);
    
    if (contexts.containsKey(uri)) {
      LOGGER.debug("Workspace {} already exists", uri);
      return contexts.get(uri);
    }

    Path rootPath = Absolute.path(uri);

    // Get new ServerContext instance from Spring (prototype scope)
    var serverContext = serverContextProvider.getObject();
    
    // Create per-workspace configuration
    var languageServerConfiguration = configurationFactory.createConfiguration(rootPath);
    serverContext.setLanguageServerConfiguration(languageServerConfiguration);
    
    // Create per-workspace DiagnosticInfo collections
    serverContext.setDiagnosticInfosByCode(
      diagnosticInfosFactory.createDiagnosticInfosByCode(languageServerConfiguration)
    );
    serverContext.setDiagnosticInfosByClass(
      diagnosticInfosFactory.createDiagnosticInfosByClass(languageServerConfiguration)
    );
    
    // Create per-workspace reference repositories
    serverContext.setReferenceContext(ReferenceContext.create());
    
    var configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(
      languageServerConfiguration, 
      rootPath
    );
    serverContext.setConfigurationRoot(configurationRoot);

    contexts.put(uri, serverContext);
    workspaceRoots.put(uri, rootPath);

    return serverContext;
  }

  /**
   * Удалить workspace folder и очистить ее контекст сервера.
   *
   * @param workspaceFolder информация о workspace folder
   */
  public void removeWorkspace(WorkspaceFolder workspaceFolder) {
    var uri = Absolute.uri(workspaceFolder.getUri());
    var serverContext = contexts.remove(uri);
    workspaceRoots.remove(uri);
    
    if (serverContext != null) {
      serverContext.clear();
    }
  }

  /**
   * Получить контекст сервера для URI документа (с нормализацией URI).
   * <p>
   * Используется для внешних вызовов, где URI может быть не нормализован.
   *
   * @param documentUri URI документа (будет нормализован)
   * @return контекст сервера, содержащий документ, или пустой Optional, если не найден
   */
  public Optional<ServerContext> getServerContextUnsafe(URI documentUri) {
    var normalizedUri = Absolute.uri(documentUri);
    return getServerContext(normalizedUri);
  }

  /**
   * Получить контекст сервера для URI документа.
   * <p>
   * Сначала ищет в индексе документов (O(1)), затем по URI workspace (для новых документов).
   * URI должен быть уже нормализован через {@link Absolute#uri(URI)}.
   * Используется {@link Absolute#uri(URI)} для канонизации, что работает со всеми схемами URI.
   *
   * @param documentUri нормализованный URI документа
   * @return контекст сервера, содержащий документ, или пустой Optional, если не найден
   */
  public Optional<ServerContext> getServerContext(URI documentUri) {
    // O(1) lookup in document index
    var indexed = documentIndex.get(documentUri);
    if (indexed != null) {
      return Optional.of(indexed);
    }
    
    // Fall back to URI-based lookup for new documents
    // URI is already normalized by caller
    var documentUriString = documentUri.toString();
    
    return contexts.keySet().stream()
      .filter(workspaceUri -> documentUriString.startsWith(workspaceUri.toString()))
      .findFirst()
      .map(contexts::get);
  }

  /**
   * Получить документ по строковому URI с нормализацией.
   * <p>
   * Ищет документ во всех зарегистрированных контекстах.
   * Используется для внешних вызовов, где URI может быть не нормализован.
   *
   * @param uri строковый URI документа
   * @return Контекст документа, если найден
   */
  public Optional<DocumentContext> getDocumentUnsafe(String uri) {
    var normalizedUri = Absolute.uri(uri);
    return getDocument(normalizedUri);
  }

  /**
   * Получить документ по URI с нормализацией.
   * <p>
   * Ищет документ во всех зарегистрированных контекстах.
   * Используется для внешних вызовов, где URI может быть не нормализован.
   *
   * @param uri URI документа (будет нормализован)
   * @return Контекст документа, если найден
   */
  public Optional<DocumentContext> getDocumentUnsafe(URI uri) {
    var normalizedUri = Absolute.uri(uri);
    return getDocument(normalizedUri);
  }

  /**
   * Получить документ по нормализованному URI.
   * <p>
   * URI должен быть уже нормализован через {@link Absolute#uri(URI)}.
   *
   * @param uri нормализованный URI документа
   * @return Контекст документа, если найден
   */
  public Optional<DocumentContext> getDocument(URI uri) {
    return getServerContext(uri)
      .flatMap(ctx -> Optional.ofNullable(ctx.getDocument(uri)));
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
    documentIndex.clear();
    LOGGER.debug("Cleared all workspaces");
  }

  /**
   * Обработчик события добавления документа в контекст.
   * Добавляет документ в индекс для быстрого поиска.
   */
  @EventListener
  public void onDocumentAdded(ServerContextDocumentAddedEvent event) {
    documentIndex.put(event.getUri(), event.getSource());
  }

  /**
   * Обработчик события удаления документа из контекста.
   * Удаляет документ из индекса.
   */
  @EventListener
  public void onDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    documentIndex.remove(event.getUri());
  }

}
