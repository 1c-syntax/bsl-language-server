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

import com.github._1c_syntax.bsl.languageserver.client.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentAddedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceBeanScope;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Провайдер контекстов сервера для мульти-workspace окружения.
 * <p>
 * Управляет коллекцией экземпляров {@link ServerContext} (по одному на каждую workspace folder)
 * и обеспечивает маршрутизацию от URI документа к контексту сервера.
 */
@Slf4j
@Component
public class ServerContextProvider {

  /**
   * Синтетический URI дефолтного («безворкспейсного») контекста. Создаётся, когда клиент
   * при инициализации не прислал ни одной workspace folder, ни {@code rootUri}/{@code rootPath}
   * (режим открытия одиночного файла или untitled-буфера). Схема не {@code file} — этот URI
   * не должен попадать в код, приводящий URI к файловому пути ({@code Absolute.path},
   * {@code Path.of}).
   */
  public static final URI DEFAULT_WORKSPACE_URI = URI.create("bsl-language-server:/default-workspace");
  private static final String DEFAULT_WORKSPACE_NAME = "default";

  private final ObjectProvider<ServerContext> serverContextObjectProvider;
  private final LanguageServerConfiguration languageServerConfiguration;
  private final WorkspaceBeanScope workspaceScope;

  private final Map<URI, ServerContext> contexts = new ConcurrentHashMap<>();
  private final Map<URI, Path> workspaceRoots = new ConcurrentHashMap<>();
  private final Map<URI, ServerContext> documentIndex = new ConcurrentHashMap<>();

  /**
   * URI «главного» workspace, в который маршрутизируются документы вне всех корней
   * (untitled-буферы, файлы вне папок проекта, одиночный файл). Предпочтение — первому
   * реальному workspace: синтетический дефолт становится главным лишь при отсутствии реальных
   * папок и вытесняется, как только реальная папка добавляется (в т.ч. в рантайме через
   * {@code didChangeWorkspaceFolders}). Хранит {@code null}, пока не зарегистрирован ни один контекст.
   */
  private final AtomicReference<@Nullable URI> primaryWorkspaceUri = new AtomicReference<>();

  public ServerContextProvider(
    ObjectProvider<ServerContext> serverContextObjectProvider,
    LanguageServerConfiguration languageServerConfiguration,
    WorkspaceBeanScope workspaceScope
  ) {
    this.serverContextObjectProvider = serverContextObjectProvider;
    this.languageServerConfiguration = languageServerConfiguration;
    this.workspaceScope = workspaceScope;
  }

  /**
   * Добавить workspace folder и создать для нее контекст сервера.
   *
   * @param workspaceFolder информация о workspace folder
   * @return созданный контекст сервера
   */
  public ServerContext addWorkspace(WorkspaceFolder workspaceFolder) {
    var uri = Absolute.uri(workspaceFolder.getUri());
    return addWorkspace(uri, workspaceFolder.getName());
  }

  /**
   * Добавить workspace по URI и создать для нее контекст сервера.
   *
   * @param workspaceUri нормализованный через {@link Absolute#uri(URI)} URI
   *                     корня workspace
   * @return созданный контекст сервера
   */
  public ServerContext addWorkspace(URI workspaceUri) {
    return addWorkspace(workspaceUri, null);
  }

  /**
   * Добавить workspace по URI и создать для нее контекст сервера.
   * <p>
   * {@code workspaceUri} должен быть нормализован через
   * {@link Absolute#uri(URI)} — иначе {@link #clear()} /
   * {@link #removeWorkspace(WorkspaceFolder)} не смогут найти запись по
   * ключу (они ищут через {@code Absolute.uri(...)} ещё раз).
   *
   * @param workspaceUri  нормализованный URI корня workspace
   * @param workspaceName имя workspace (если null — извлекается из URI)
   * @return созданный контекст сервера
   */
  public ServerContext addWorkspace(URI workspaceUri, @Nullable String workspaceName) {

    if (contexts.containsKey(workspaceUri)) {
      LOGGER.debug("Workspace {} already exists", workspaceUri);
      return contexts.get(workspaceUri);
    }

    var rootPath = Absolute.path(workspaceUri);

    var name = workspaceName != null ? workspaceName : extractWorkspaceName(workspaceUri);
    WorkspaceContextHolder.registerWorkspace(workspaceUri, name);

    // Set workspace context for scoped bean resolution
    try (var ctx = WorkspaceContextHolder.forUri(workspaceUri)) {
      // Get new ServerContext instance from Spring (prototype scope)
      var serverContext = serverContextObjectProvider.getObject();

      serverContext.setWorkspaceUri(workspaceUri);

      // Access workspace-scoped LSC (triggers lazy creation with @PostConstruct init())
      // and store on ServerContext for navigation-based access
      serverContext.setLanguageServerConfiguration(languageServerConfiguration);

      var configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(
        languageServerConfiguration,
        rootPath
      );
      serverContext.setConfigurationRoot(configurationRoot);

      contexts.put(workspaceUri, serverContext);
      workspaceRoots.put(workspaceUri, rootPath);
      promoteToPrimary(workspaceUri);

      return serverContext;
    }
  }

  /**
   * Создать дефолтный («безворкспейсный») контекст сервера.
   * <p>
   * Используется, когда при инициализации не переданы ни workspace folders, ни
   * {@code rootUri}/{@code rootPath} (открыт одиночный файл или untitled-буфер). Контекст
   * создаётся с {@code configurationRoot == null}, поэтому {@link ServerContext#populateContext()}
   * ничего не сканирует. Документы, не попавшие ни в один workspace, маршрутизируются в него
   * (или в первый добавленный workspace) — см. {@link #resolveContextForDocument(URI)}.
   * <p>
   * В отличие от {@link #addWorkspace(URI, String)}, метод намеренно не публикует
   * {@code WorkspaceAddedEvent}: синтетический URI не является файловым, и подписчики,
   * приводящие URI к пути (наблюдатели файлов конфигурации/библиотек), на нём падали бы.
   *
   * @return дефолтный контекст сервера (идемпотентно: повторные вызовы возвращают существующий)
   */
  public ServerContext registerDefaultWorkspace() {
    var existing = contexts.get(DEFAULT_WORKSPACE_URI);
    if (existing != null) {
      return existing;
    }

    WorkspaceContextHolder.registerWorkspace(DEFAULT_WORKSPACE_URI, DEFAULT_WORKSPACE_NAME);

    try (var ctx = WorkspaceContextHolder.forUri(DEFAULT_WORKSPACE_URI)) {
      var serverContext = serverContextObjectProvider.getObject();

      serverContext.setWorkspaceUri(DEFAULT_WORKSPACE_URI);
      serverContext.setLanguageServerConfiguration(languageServerConfiguration);
      // configurationRoot остаётся null: сканировать нечего, populateContext() выйдет сразу.

      contexts.put(DEFAULT_WORKSPACE_URI, serverContext);
      markPrimaryIfAbsent(DEFAULT_WORKSPACE_URI);

      return serverContext;
    }
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

    // serverContext.clear() публикует ServerContextDocumentRemovedEvent через AOP
    // для каждого удалённого документа; подписчики (workspace-scoped — ReferenceIndexFiller,
    // OScriptLibraryIndex, etc.) должны иметь возможность резолвиться. Поэтому событие должно
    // отлететь ДО уничтожения scope, и в этом thread'е должен быть выставлен workspaceUri.
    // Two-arg forUri используем, чтобы не требовать наличие URI в WORKSPACE_NAMES
    // (для async-propagated workspace'ов запись там может отсутствовать).
    if (serverContext != null) {
      var name = extractWorkspaceName(uri);
      try (var ctx = WorkspaceContextHolder.forUri(uri, name)) {
        serverContext.clear();
      }
    }

    workspaceScope.removeWorkspace(uri);
    WorkspaceContextHolder.unregisterWorkspace(uri);
    repointPrimaryIfRemoved(uri);
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
   * Определить контекст сервера для документа с фолбэком на главный workspace.
   * <p>
   * Сначала ищет владеющий контекст по правилам {@link #getServerContext(URI)} (индекс, затем
   * префикс URI workspace). Если документ не относится ни к одному корню (untitled-буфер, файл
   * вне папок проекта, одиночный файл), маршрутизирует его в главный контекст
   * ({@link #getPrimaryContext()}). URI нормализуется через {@link Absolute#uri(URI)}.
   *
   * @param documentUri URI документа (будет нормализован)
   * @return контекст сервера для документа или пустой Optional,
   *         если не зарегистрирован ни один контекст
   */
  public Optional<ServerContext> resolveContextForDocument(URI documentUri) {
    var normalizedUri = Absolute.uri(documentUri);
    var direct = getServerContext(normalizedUri);
    if (direct.isPresent()) {
      return direct;
    }
    return getPrimaryContext();
  }

  /**
   * Получить главный контекст — первый зарегистрированный workspace (или дефолтный контекст
   * в режиме одиночного файла). Package-private: используется внутри
   * {@link #resolveContextForDocument(URI)} и в тестах пакета.
   *
   * @return главный контекст сервера или пустой Optional,
   *         если не зарегистрирован ни один контекст
   */
  Optional<ServerContext> getPrimaryContext() {
    var uri = primaryWorkspaceUri.get();
    if (uri == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(contexts.get(uri));
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
   * Получить документ по URI без захвата per-document RWLock.
   * <p>
   * Используется горячими путями инференции (reference finders, type
   * inferencer), которые массово опрашивают документы во время
   * {@code populateContext}. RWLock с queued writer'ом блокирует новых
   * readers (fair-mode), что приводит к парку всех worker-потоков.
   * <p>
   * Безопасно для read-only-доступа: документы не пересоздаются, они
   * обновляются in-place под write-lock'ом; чтение без лока даёт snapshot
   * текущего AST/symbol-tree, что допустимо для type-инференции (та и без
   * того eventually-consistent относительно правок пользователя).
   */
  public Optional<DocumentContext> getDocumentNoLock(URI uri) {
    return getServerContext(uri)
      .flatMap(ctx -> Optional.ofNullable(ctx.getDocumentNoLock(uri)));
  }

  /**
   * То же, что {@link #getDocumentNoLock(URI)}, но с нормализацией.
   * Аналог {@link #getDocumentUnsafe(URI)} для контекстов, где нельзя
   * брать per-document RWLock (см. документацию к {@link #getDocumentNoLock(URI)}).
   */
  public Optional<DocumentContext> getDocumentUnsafeNoLock(URI uri) {
    var normalizedUri = Absolute.uri(uri);
    return getDocumentNoLock(normalizedUri);
  }

  /**
   * Получить все контексты серверов с их URI.
   *
   * @return неизменяемая карта URI → контекст сервера
   */
  public Map<URI, ServerContext> getAllContexts() {
    return Collections.unmodifiableMap(contexts);
  }

  /**
   * Очистить все workspaces.
   */
  public void clear() {
    new ArrayList<>(contexts.keySet()).forEach(uri ->
      removeWorkspace(new WorkspaceFolder(uri.toString(), uri.toString()))
    );
    documentIndex.clear();
    primaryWorkspaceUri.set(null);
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

  /**
   * Назначить главным дефолтный контекст, только если главный ещё не выбран
   * (реальные папки при инициализации не пришли).
   */
  private void markPrimaryIfAbsent(URI workspaceUri) {
    primaryWorkspaceUri.compareAndSet(null, workspaceUri);
  }

  /**
   * Назначить реальный workspace главным, если главного нет либо им сейчас является
   * синтетический дефолт. Так первая реальная папка (в т.ч. добавленная в рантайме) вытесняет
   * дефолт, а последующие реальные папки главного не меняют.
   */
  private void promoteToPrimary(URI workspaceUri) {
    primaryWorkspaceUri.updateAndGet(current ->
      (current == null || DEFAULT_WORKSPACE_URI.equals(current)) ? workspaceUri : current
    );
  }

  /**
   * Переназначить главный контекст при удалении текущего главного: предпочитаем оставшийся
   * реальный workspace синтетическому дефолту, дефолт — как последний вариант.
   */
  private void repointPrimaryIfRemoved(URI removedUri) {
    primaryWorkspaceUri.updateAndGet(current -> {
      if (!removedUri.equals(current)) {
        return current;
      }
      return contexts.keySet().stream()
        .filter(uri -> !DEFAULT_WORKSPACE_URI.equals(uri))
        .findFirst()
        .orElseGet(() -> contexts.containsKey(DEFAULT_WORKSPACE_URI) ? DEFAULT_WORKSPACE_URI : null);
    });
  }

  private static String extractWorkspaceName(URI workspaceUri) {
    var path = workspaceUri.getPath();
    if (path == null) {
      return workspaceUri.toString();
    }
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    var lastSlash = path.lastIndexOf('/');
    return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
  }

}
