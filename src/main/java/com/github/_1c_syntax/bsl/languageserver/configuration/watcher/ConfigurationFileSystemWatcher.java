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
package com.github._1c_syntax.bsl.languageserver.configuration.watcher;

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.GlobalLanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.events.BeforeWorkspaceRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.events.WorkspaceAddedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Отслеживатель изменений файлов конфигурации.
 * <p>
 * Мониторит глобальный файл конфигурации и файлы конфигурации каждого workspace.
 * При обнаружении изменения (удаление, создание, редактирование) делегирует обработку в
 * {@link ConfigurationFileChangeListener}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConfigurationFileSystemWatcher {

  private static final String CONFIG_FILE_NAME = ".bsl-language-server.json";

  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final ConfigurationFileChangeListener listener;
  /**
   * Провайдер workspace-scoped конфигурации. Под выставленным {@link WorkspaceContextHolder}
   * (на момент {@link WorkspaceAddedEvent}) {@code getObject()} возвращает конкретный экземпляр
   * {@link LanguageServerConfiguration} для добавляемого workspace — тот же бин, что держит его
   * {@code ServerContext}, но без зависимости конфигурации от пакета context.
   */
  private final ObjectProvider<LanguageServerConfiguration> workspaceConfigurationProvider;

  @SuppressWarnings("NullAway.Init")
  private WatchService watchService;
  @Nullable
  private Path globalRegisteredPath;
  @Nullable
  private WatchKey globalWatchKey;

  // Per-workspace config watching
  private final Map<URI, WatchKey> workspaceWatchKeys = new ConcurrentHashMap<>();
  private final Map<URI, Path> workspaceRegisteredPaths = new ConcurrentHashMap<>();
  private final Map<URI, LanguageServerConfiguration> workspaceConfigurations = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() throws IOException {
    watchService = FileSystems.getDefault().newWatchService();
  }

  @PreDestroy
  @Synchronized
  public void onDestroy() throws IOException {
    if (globalWatchKey != null) {
      globalWatchKey.cancel();
    }
    workspaceWatchKeys.values().forEach(WatchKey::cancel);
    workspaceWatchKeys.clear();
    workspaceRegisteredPaths.clear();
    workspaceConfigurations.clear();
    watchService.close();
  }

  /**
   * Фоновая процедура, отслеживающая изменения файлов.
   * <p>
   * Java NIO {@code WatchService.register} для уже зарегистрированной директории
   * возвращает <b>тот же</b> {@link WatchKey} (см. javadoc); поэтому
   * {@link #globalWatchKey} и {@code workspaceWatchKeys.get(uri)} могут быть
   * физически одним и тем же объектом, когда workspace LSC при init подтянул
   * настройки из глобального файла и {@code configurationFile} workspace
   * указывает на ту же директорию. В таком случае «два listener'а на один
   * файл» нельзя реализовать через два независимых ключа: первый
   * {@code pollEvents()} consume-ит события у другого.
   * <p>
   * Решение — диспатч по файлу: один проход по уникальным ключам, события
   * каждого ключа распределяются между всеми заинтересованными listener'ами
   * (global + workspace LSC, у которых файл совпадает с
   * {@link LanguageServerConfiguration#getConfigurationFile()}).
   */
  @Scheduled(fixedDelay = 5000L)
  @Synchronized
  public void watch() {
    var processedKeys = new java.util.IdentityHashMap<WatchKey, Path>();
    if (globalWatchKey != null && globalRegisteredPath != null) {
      processedKeys.putIfAbsent(globalWatchKey, globalRegisteredPath);
    }
    for (var entry : workspaceWatchKeys.entrySet()) {
      var path = workspaceRegisteredPaths.get(entry.getKey());
      if (path != null) {
        processedKeys.putIfAbsent(entry.getValue(), path);
      }
    }
    processedKeys.forEach(this::pollAndDispatch);
  }

  private void pollAndDispatch(WatchKey key, Path registeredPath) {
    long lastModified = 0L;
    for (WatchEvent<?> watchEvent : key.pollEvents()) {
      var context = (Path) watchEvent.context();
      if (context == null) {
        continue;
      }
      var file = new File(registeredPath.toFile(), context.toFile().getName());
      if (file.lastModified() == lastModified && !ENTRY_DELETE.equals(watchEvent.kind())) {
        continue;
      }
      lastModified = file.lastModified();
      dispatch(file, watchEvent.kind());
    }
    key.reset();
  }

  private void dispatch(File file, WatchEvent.Kind<?> eventKind) {
    if (isGlobalConfigurationFile(file)) {
      listener.onGlobalChange(file, eventKind);
    }
    workspaceConfigurations.forEach((workspaceUri, configuration) ->
      WorkspaceContextHolder.run(workspaceUri, () -> {
        if (isWorkspaceConfigurationFile(file, configuration)) {
          listener.onWorkspaceChange(file, eventKind, configuration, workspaceUri);
        }
      })
    );
  }

  /**
   * Обработчик события {@link GlobalLanguageServerConfigurationChangedEvent}.
   *
   * @param event Событие
   */
  @EventListener
  public void handleGlobalConfigurationChanged(GlobalLanguageServerConfigurationChangedEvent event) {
    var configFile = event.getSource().getConfigurationFile();
    if (configFile != null) {
      registerGlobalWatchService(configFile);
    }
  }

  /**
   * Обработчик добавления нового workspace.
   * <p>
   * На момент публикации {@link WorkspaceAddedEvent} workspace-контекст уже выставлен в
   * {@link WorkspaceContextHolder}, поэтому {@code workspaceConfigurationProvider.getObject()}
   * возвращает конкретный экземпляр конфигурации именно этого workspace.
   *
   * @param event Событие добавления workspace
   */
  @EventListener
  @Synchronized
  public void handleWorkspaceAdded(WorkspaceAddedEvent event) {
    var workspaceUri = event.getWorkspaceUri();
    var configuration = workspaceConfigurationProvider.getObject();
    LOGGER.debug("Workspace added, registering config watcher: {}", workspaceUri);
    registerWorkspaceWatchService(workspaceUri, configuration);
  }

  /**
   * Обработчик удаления workspace (перед удалением).
   *
   * @param event Событие удаления workspace
   */
  @EventListener
  @Synchronized
  public void handleBeforeWorkspaceRemoved(BeforeWorkspaceRemovedEvent event) {
    var workspaceUri = event.getWorkspaceUri();

    LOGGER.debug("Workspace being removed, canceling config watcher: {}", workspaceUri);
    var watchKey = workspaceWatchKeys.remove(workspaceUri);
    if (watchKey != null) {
      watchKey.cancel();
    }
    workspaceRegisteredPaths.remove(workspaceUri);
    workspaceConfigurations.remove(workspaceUri);
  }

  @SneakyThrows
  private void registerGlobalWatchService(File configurationFile) {
    Path configurationDir = Absolute.path(configurationFile).getParent();

    if (configurationDir == null) {
      return;
    }

    if (configurationDir.equals(globalRegisteredPath)) {
      return;
    }

    if (globalWatchKey != null) {
      globalWatchKey.cancel();
    }

    globalRegisteredPath = configurationDir;

    globalWatchKey = globalRegisteredPath.register(
      watchService,
      ENTRY_CREATE,
      ENTRY_DELETE,
      ENTRY_MODIFY
    );

    LOGGER.debug("Watch for global configuration file changes in {}", configurationDir);
  }

  @SneakyThrows
  private void registerWorkspaceWatchService(URI workspaceUri, LanguageServerConfiguration configuration) {
    var configFile = configuration.getConfigurationFile();

    Path configDir;
    if (configFile != null) {
      configDir = Absolute.path(configFile).getParent();
    } else {
      // Default to workspace root for config file watching
      configDir = Path.of(workspaceUri);
    }

    if (configDir == null || !configDir.toFile().isDirectory()) {
      return;
    }

    // Check if already watching this path
    if (configDir.equals(workspaceRegisteredPaths.get(workspaceUri))) {
      return;
    }

    // Cancel previous watch if exists
    var existingWatchKey = workspaceWatchKeys.get(workspaceUri);
    if (existingWatchKey != null) {
      existingWatchKey.cancel();
    }

    var watchKey = configDir.register(
      watchService,
      ENTRY_CREATE,
      ENTRY_DELETE,
      ENTRY_MODIFY
    );

    workspaceWatchKeys.put(workspaceUri, watchKey);
    workspaceRegisteredPaths.put(workspaceUri, configDir);
    workspaceConfigurations.put(workspaceUri, configuration);

    LOGGER.debug("Watch for workspace configuration file changes in {}", configDir);
  }

  private boolean isGlobalConfigurationFile(File pathname) {
    var configFile = globalConfiguration.getConfigurationFile();
    if (configFile == null) {
      return false;
    }
    var absolutePathname = Absolute.path(pathname);
    var absoluteConfigurationFile = Absolute.path(configFile);
    return absolutePathname.equals(absoluteConfigurationFile);
  }

  private static boolean isWorkspaceConfigurationFile(File pathname, LanguageServerConfiguration configuration) {
    var configFile = configuration.getConfigurationFile();
    if (configFile != null) {
      var absolutePathname = Absolute.path(pathname);
      var absoluteConfigurationFile = Absolute.path(configFile);
      return absolutePathname.equals(absoluteConfigurationFile);
    }
    // If no config file set, check for default config file name
    return CONFIG_FILE_NAME.equals(pathname.getName());
  }
}
