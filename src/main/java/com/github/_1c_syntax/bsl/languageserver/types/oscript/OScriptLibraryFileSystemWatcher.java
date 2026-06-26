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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.BeforeWorkspaceRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.oscript.events.OScriptLibraryIndexedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Отслеживает изменения {@code lib.config} в директориях найденных
 * OneScript-библиотек workspace и при событии create/modify/delete
 * вызывает {@link OScriptLibraryIndex#reindex(ServerContext)}.
 * <p>
 * Подход тот же, что в {@code ConfigurationFileSystemWatcher}: один singleton
 * {@link WatchService} на JVM, регистрация per-workspace на родительские
 * директории найденных манифестов (поскольку Java NIO не позволяет
 * регистрировать watch на отдельный файл — только на директорию),
 * polling через {@code @Scheduled(fixedDelay=5000)}, фильтр событий
 * по имени файла {@value LibConfigDiscovery#LIB_CONFIG_FILENAME}.
 * <p>
 * Подписывается на {@link OScriptLibraryIndexedEvent} после каждого reindex,
 * чтобы актуализировать набор отслеживаемых директорий: новые добавляет,
 * исчезнувшие — отменяет. На {@link BeforeWorkspaceRemovedEvent}
 * освобождает все watch'и workspace'а.
 * <p>
 * Ограничение: {@link WatchService} не рекурсивен, отслеживаются только
 * директории <em>уже найденных</em> {@code lib.config}. Появление нового
 * манифеста в неизвестной директории глубоко в дереве (например, после
 * {@code opm install <new-pkg>}) не приведёт к автоматическому reindex —
 * нужен явный триггер (рестарт LS).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OScriptLibraryFileSystemWatcher {

  private static final String LIB_CONFIG_FILENAME = LibConfigDiscovery.LIB_CONFIG_FILENAME;

  @SuppressWarnings("NullAway.Init")
  private WatchService watchService;

  /** URI workspace → его {@link ServerContext}. Нужно для последующего вызова reindex. */
  private final Map<URI, ServerContext> contexts = new ConcurrentHashMap<>();
  /** URI workspace → ссылка на индекс (workspace-scoped, кэшируем после события). */
  private final Map<URI, OScriptLibraryIndex> indexByWorkspace = new ConcurrentHashMap<>();
  /** URI workspace → активные {@link WatchKey} по директории. */
  private final Map<URI, Map<Path, WatchKey>> watchKeysByWorkspace = new ConcurrentHashMap<>();
  /** Обратный индекс: ключ → workspace URI (для маршрутизации событий polling'а). */
  private final Map<WatchKey, URI> workspaceByKey = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() throws IOException {
    watchService = FileSystems.getDefault().newWatchService();
  }

  @PreDestroy
  @Synchronized
  public void onDestroy() throws IOException {
    watchKeysByWorkspace.values().forEach(map -> map.values().forEach(WatchKey::cancel));
    watchKeysByWorkspace.clear();
    workspaceByKey.clear();
    contexts.clear();
    indexByWorkspace.clear();
    watchService.close();
  }

  /**
   * После каждого reindex обновляем набор отслеживаемых директорий:
   * добавляем новые, отменяем исчезнувшие.
   */
  @EventListener
  @Synchronized
  public void handleIndexed(OScriptLibraryIndexedEvent event) {
    var serverContext = event.getServerContext();
    var workspaceUri = serverContext.getWorkspaceUri();
    if (workspaceUri == null) {
      return;
    }
    contexts.put(workspaceUri, serverContext);
    indexByWorkspace.put(workspaceUri, (OScriptLibraryIndex) event.getSource());

    var wantedDirs = new HashSet<Path>();
    for (var libConfig : event.getLibConfigPaths()) {
      var parent = libConfig.getParent();
      if (parent != null) {
        wantedDirs.add(parent.toAbsolutePath().normalize());
      }
    }

    var existing = watchKeysByWorkspace.computeIfAbsent(workspaceUri, k -> new HashMap<>());

    // Отменяем watch'и для исчезнувших директорий.
    var toRemove = new HashSet<Path>();
    for (var dir : existing.keySet()) {
      if (!wantedDirs.contains(dir)) {
        toRemove.add(dir);
      }
    }
    for (var dir : toRemove) {
      var key = existing.remove(dir);
      if (key != null) {
        key.cancel();
        workspaceByKey.remove(key);
      }
    }

    // Регистрируем новые.
    for (var dir : wantedDirs) {
      if (existing.containsKey(dir)) {
        continue;
      }
      try {
        var key = registerDir(dir);
        if (key != null) {
          existing.put(dir, key);
          workspaceByKey.put(key, workspaceUri);
        }
      } catch (IOException e) {
        LOGGER.debug("Failed to register lib.config watch for {}: {}", dir, e.toString());
      }
    }
  }

  @EventListener
  @Synchronized
  public void handleBeforeWorkspaceRemoved(BeforeWorkspaceRemovedEvent event) {
    var workspaceUri = event.getWorkspaceUri();
    var keys = watchKeysByWorkspace.remove(workspaceUri);
    if (keys != null) {
      keys.values().forEach(k -> {
        k.cancel();
        workspaceByKey.remove(k);
      });
    }
    contexts.remove(workspaceUri);
    indexByWorkspace.remove(workspaceUri);
  }

  /**
   * Polling-loop. Опрашивает накопленные события; за один tick события
   * по одному workspace дедуплицируются — reindex вызывается один раз.
   */
  @Scheduled(fixedDelay = 5000L)
  @Synchronized
  public void poll() {
    var toReindex = new HashSet<URI>();
    var snapshot = Map.copyOf(workspaceByKey);
    for (var entry : snapshot.entrySet()) {
      var key = entry.getKey();
      var workspaceUri = entry.getValue();
      if (consumeLibConfigEvent(key)) {
        toReindex.add(workspaceUri);
      }
    }
    for (var workspaceUri : toReindex) {
      var serverContext = contexts.get(workspaceUri);
      var index = indexByWorkspace.get(workspaceUri);
      if (serverContext == null || index == null) {
        continue;
      }
      WorkspaceContextHolder.run(workspaceUri, () -> {
        try {
          LOGGER.debug("lib.config changed, reindexing OneScript libraries for {}", workspaceUri);
          index.reindex(serverContext);
        } catch (RuntimeException e) {
          LOGGER.warn("Failed to reindex OneScript libraries for {}", workspaceUri, e);
        }
      });
    }
  }

  /**
   * @return {@code true}, если среди событий ключа есть хотя бы одно по файлу
   *         {@value #LIB_CONFIG_FILENAME}.
   */
  private static boolean consumeLibConfigEvent(WatchKey key) {
    var triggered = false;
    for (WatchEvent<?> event : key.pollEvents()) {
      var context = event.context();
      if (!(context instanceof Path relPath)) {
        continue;
      }
      var name = relPath.getFileName();
      if (name == null) {
        continue;
      }
      if (LIB_CONFIG_FILENAME.equals(name.toString())) {
        triggered = true;
      }
    }
    key.reset();
    return triggered;
  }

  @Nullable
  private WatchKey registerDir(Path dir) throws IOException {
    if (!dir.toFile().isDirectory()) {
      return null;
    }
    return dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
  }
}
