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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Поиск манифестов {@code lib.config} OneScript-библиотек.
 * <p>
 * Источники:
 * <ol>
 *   <li>Корень workspace (рекурсивно, глубина ≤ {@value #MAX_DEPTH}).</li>
 *   <li>Каталоги из {@code oscript.libRoots} (относительные пути — от корня
 *       workspace, абсолютные — как есть).</li>
 *   <li>Если включено {@code oscript.useEnvLibLocation} — пути из переменной
 *       окружения {@code OSCRIPT_LIB_LOCATION} (разделитель —
 *       {@link File#pathSeparator}).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LibConfigDiscovery {

  /** Имя файла-манифеста. */
  public static final String LIB_CONFIG_FILENAME = "lib.config";

  /** Имя стандартного каталога локальных OneScript-зависимостей. */
  public static final String OSCRIPT_MODULES_DIRNAME = "oscript_modules";

  /** Имя переменной окружения с дополнительными корнями. */
  public static final String ENV_LIB_LOCATION = "OSCRIPT_LIB_LOCATION";

  /** Максимальная глубина обхода в поисках {@code lib.config}. */
  static final int MAX_DEPTH = 6;

  private final LanguageServerConfiguration configuration;

  /**
   * Найти все {@code lib.config} для указанного workspace-контекста.
   *
   * @param serverContext workspace-контекст; если у него нет корня,
   *                      используются только глобальные источники
   * @return список абсолютных нормализованных путей, без дубликатов
   */
  public List<Path> discover(ServerContext serverContext) {
    var workspaceRoot = serverContext.getConfigurationRoot();
    return discover(workspaceRoot);
  }

  /**
   * Найти все {@code lib.config} для указанного корня workspace.
   */
  public List<Path> discover(Path workspaceRoot) {
    var result = new LinkedHashSet<Path>();
    for (var root : getRoots(workspaceRoot)) {
      scan(root, result);
    }
    return new ArrayList<>(result);
  }

  /**
   * Корни для поиска OneScript-библиотек (workspace + {@code libRoots} +
   * опционально {@code OSCRIPT_LIB_LOCATION}). Используется не только для
   * поиска {@code lib.config}, но и для convention-based discovery.
   */
  public List<Path> getRoots(ServerContext serverContext) {
    return getRoots(serverContext == null ? null : serverContext.getConfigurationRoot());
  }

  /**
   * См. {@link #getRoots(ServerContext)}.
   */
  public List<Path> getRoots(Path workspaceRoot) {
    Set<Path> roots = new LinkedHashSet<>();
    if (workspaceRoot != null) {
      var wsAbs = workspaceRoot.toAbsolutePath().normalize();
      roots.add(wsAbs);
      // Стандартный каталог локальных oscript-зависимостей: каждый
      // непосредственный подкаталог считается отдельным корнем библиотеки.
      addOscriptModulesChildren(wsAbs, roots);
    }

    var opts = configuration.getOscriptOptions();
    for (var lr : opts.getLibRoots()) {
      var p = Path.of(lr);
      if (!p.isAbsolute() && workspaceRoot != null) {
        p = workspaceRoot.resolve(p);
      }
      roots.add(p.toAbsolutePath().normalize());
    }
    if (opts.isUseEnvLibLocation()) {
      var env = System.getenv(ENV_LIB_LOCATION);
      if (env != null && !env.isBlank()) {
        for (var part : env.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
          if (!part.isBlank()) {
            roots.add(Path.of(part).toAbsolutePath().normalize());
          }
        }
      }
    }
    return new ArrayList<>(roots);
  }

  private static void addOscriptModulesChildren(Path workspaceRoot, Set<Path> sink) {
    var modulesDir = workspaceRoot.resolve(OSCRIPT_MODULES_DIRNAME);
    if (!Files.isDirectory(modulesDir)) {
      return;
    }
    try (var stream = Files.list(modulesDir)) {
      stream
        .filter(Files::isDirectory)
        .map(p -> p.toAbsolutePath().normalize())
        .forEach(sink::add);
    } catch (IOException e) {
      LOGGER.debug("Skipping unreadable oscript_modules directory: {}", modulesDir, e);
    }
  }

  private static void scan(Path root, Set<Path> sink) {
    if (!Files.isDirectory(root)) {
      return;
    }
    try {
      Files.walkFileTree(root, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
          if (LIB_CONFIG_FILENAME.equals(name)) {
            sink.add(file.toAbsolutePath().normalize());
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
          LOGGER.debug("Skipping unreadable path while scanning lib.config: {}", file, exc);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      LOGGER.warn("Failed to scan oscript library root: {}", root, e);
    }
  }
}
