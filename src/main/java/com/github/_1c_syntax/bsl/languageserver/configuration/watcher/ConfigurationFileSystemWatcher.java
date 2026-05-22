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
import com.github._1c_syntax.bsl.languageserver.context.events.BeforeWorkspaceRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.WorkspaceAddedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import com.sun.nio.file.SensitivityWatchEventModifier;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
@SuppressWarnings("removal") // SensitivityWatchEventModifier is deprecated in jdk21
public class ConfigurationFileSystemWatcher {

  private static final String CONFIG_FILE_NAME = ".bsl-language-server.json";

  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final ConfigurationFileChangeListener listener;

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
   */
  @Scheduled(fixedDelay = 5000L)
  @Synchronized
  public void watch() {
    // Watch global config
    if (globalWatchKey != null) {
      watchGlobalConfig();
    }

    // Watch per-workspace configs
    for (var entry : workspaceWatchKeys.entrySet()) {
      var workspaceUri = entry.getKey();
      var watchKey = entry.getValue();
      WorkspaceContextHolder.run(workspaceUri, () ->
        watchWorkspaceConfig(workspaceUri, watchKey)
      );
    }
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
   * Workspace-контекст уже установлен в {@link com.github._1c_syntax.bsl.languageserver.aop.EventPublisherAspect}
   * перед публикацией {@link WorkspaceAddedEvent}, поэтому прямое обращение к workspace-scoped прокси
   * через {@code event.getServerContext().getLanguageServerConfiguration()} корректно.
   *
   * @param event Событие добавления workspace
   */
  @EventListener
  @Synchronized
  public void handleWorkspaceAdded(WorkspaceAddedEvent event) {
    var workspaceUri = event.getWorkspaceUri();
    var configuration = event.getServerContext().getLanguageServerConfiguration();
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

  private void watchGlobalConfig() {
    long lastModified = 0L;
    for (WatchEvent<?> watchEvent : globalWatchKey.pollEvents()) {
      var context = (Path) watchEvent.context();
      if (context == null) {
        continue;
      }

      var file = new File(globalRegisteredPath.toFile(), context.toFile().getName());
      if (isGlobalConfigurationFile(file)
        && (file.lastModified() != lastModified || watchEvent.kind().equals(ENTRY_DELETE))) {
        lastModified = file.lastModified();
        listener.onGlobalChange(file, watchEvent.kind());
        // Workspace LSC, у которых нет своего конфиг-файла, во время init()
        // подтянули настройки из глобального файла и закрепили его как
        // {@code configurationFile}. При его runtime-изменении эти workspace
        // LSC тоже должны перечитаться — иначе workspace-level настройки
        // (inlayHintOptions, diagnosticsOptions и т.д.) остаются «замороженными»
        // с момента init.
        propagateGlobalChangeToWorkspaces(file, watchEvent.kind());
      }
    }
    globalWatchKey.reset();
  }

  private void propagateGlobalChangeToWorkspaces(File globalFile, WatchEvent.Kind<?> eventKind) {
    // Workspace LSC — workspace-scoped proxy; обращение к нему вне WorkspaceContextHolder
    // выкидывает ScopeNotActiveException. Заходим в скоуп каждого workspace отдельно
    // (так же как watchWorkspaceConfig делает свою итерацию).
    // {@link File#equals} сравнивает path как строку, поэтому relative
    // {@code .bsl-language-server.json} != absolute. Нормализуем оба через
    // {@code getAbsoluteFile}.
    var globalAbsolute = globalFile.getAbsoluteFile();
    workspaceConfigurations.forEach((workspaceUri, configuration) ->
      WorkspaceContextHolder.run(workspaceUri, () -> {
        var workspaceFile = configuration.getConfigurationFile();
        if (workspaceFile != null && globalAbsolute.equals(workspaceFile.getAbsoluteFile())) {
          listener.onWorkspaceChange(globalFile, eventKind, configuration, workspaceUri);
        }
      })
    );
  }

  private void watchWorkspaceConfig(URI workspaceUri, WatchKey watchKey) {
    var registeredPath = workspaceRegisteredPaths.get(workspaceUri);
    var configuration = workspaceConfigurations.get(workspaceUri);

    if (registeredPath == null || configuration == null) {
      return;
    }

    long lastModified = 0L;
    for (WatchEvent<?> watchEvent : watchKey.pollEvents()) {
      Path context = (Path) watchEvent.context();
      if (context == null) {
        continue;
      }

      var file = new File(registeredPath.toFile(), context.toFile().getName());
      if (isWorkspaceConfigurationFile(file, configuration)
        && (file.lastModified() != lastModified || watchEvent.kind().equals(ENTRY_DELETE))) {
        lastModified = file.lastModified();
        listener.onWorkspaceChange(file, watchEvent.kind(), configuration, workspaceUri);
      }
    }
    watchKey.reset();
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

    // TODO: SensitivityWatchEventModifier is deprecated in java 21 and marked for removal.
    // We need to drop usage of it here when we change our baseline to jdk 21
    globalWatchKey = globalRegisteredPath.register(
      watchService,
      new WatchEvent.Kind[]{
        ENTRY_CREATE,
        ENTRY_DELETE,
        ENTRY_MODIFY
      },
      SensitivityWatchEventModifier.HIGH
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

    // TODO: SensitivityWatchEventModifier is deprecated in java 21 and marked for removal.
    var watchKey = configDir.register(
      watchService,
      new WatchEvent.Kind[]{
        ENTRY_CREATE,
        ENTRY_DELETE,
        ENTRY_MODIFY
      },
      SensitivityWatchEventModifier.HIGH
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
