/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.utils.Absolute;
import com.sun.nio.file.SensitivityWatchEventModifier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Отслеживатель изменений файла конфигурации.
 * <p>
 * При обнаружении изменения в файле (удаление, создание, редактирование) делегирует обработку изменения в
 * {@link ConfigurationFileChangeListener}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConfigurationFileSystemWatcher {

  private final LanguageServerConfiguration configuration;
  private final ConfigurationFileChangeListener listener;

  private Path registeredPath;
  private WatchService watchService;
  private WatchKey watchKey;

  @PostConstruct
  public void init() throws IOException {
    watchService = FileSystems.getDefault().newWatchService();
    registerWatchService(configuration.getConfigurationFile());
  }

  @PreDestroy
  @Synchronized
  public void onDestroy() throws IOException {
    watchKey.cancel();
    watchService.close();
  }

  /**
   * Фоновая процедура, отслеживающая изменения файлов.
   */
  @Scheduled(fixedDelay = 5000L)
  @Synchronized
  public void watch() {
    // save last modified date to de-duplicate events
    long lastModified = 0L;
    for (WatchEvent<?> watchEvent : watchKey.pollEvents()) {
      Path context = (Path) watchEvent.context();
      if (context == null) {
        continue;
      }

      var file = new File(registeredPath.toFile(), context.toFile().getName());
      if (isConfigurationFile(file)
        && (file.lastModified() != lastModified || watchEvent.kind().equals(ENTRY_DELETE))) {
        lastModified = file.lastModified();
        listener.onChange(file, watchEvent.kind());
      }
    }

    watchKey.reset();
  }

  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    registerWatchService(event.getSource().getConfigurationFile());
  }

  @SneakyThrows
  private void registerWatchService(File configurationFile) {
    Path configurationDir = Absolute.path(configurationFile).getParent();

    if (configurationDir == null) {
      return;
    }

    if (configurationDir.equals(registeredPath)) {
      return;
    }

    if (watchKey != null) {
      watchKey.cancel();
    }

    registeredPath = configurationDir;

    watchKey = registeredPath.register(
      watchService,
      new WatchEvent.Kind[]{
        ENTRY_CREATE,
        ENTRY_DELETE,
        ENTRY_MODIFY
      },
      SensitivityWatchEventModifier.HIGH
    );

    LOGGER.debug("Watch for configuration file changes in {}", configurationDir);
  }

  private boolean isConfigurationFile(File pathname) {
    var absolutePathname = Absolute.path(pathname);
    var absoluteConfigurationFile = Absolute.path(configuration.getConfigurationFile());
    return absolutePathname.equals(absoluteConfigurationFile);
  }

}
