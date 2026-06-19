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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Convention-based fallback для OneScript-библиотек без {@code lib.config}.
 * <p>
 * В каталоге библиотеки (корне oscript-проекта или непосредственном подкаталоге
 * из списка {@code libRoots}) ищутся подкаталоги {@code Классы}/{@code Classes}
 * и {@code Модули}/{@code Modules}; каждый {@code .os}-файл в них регистрируется
 * как класс/модуль соответственно (имя — basename файла без расширения).
 * <p>
 * Если в каталоге библиотеки присутствует {@code lib.config} — конвенциональный
 * проход для этого каталога пропускается: библиотеку индексирует
 * {@link LibConfigDiscovery}/{@link LibConfigParser} в обычном режиме.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConventionalLibraryDiscovery {

  /** Имена convention-каталогов для классов. */
  public static final List<String> CLASS_DIRS = List.of("Классы", "Classes");
  /** Имена convention-каталогов для модулей. */
  public static final List<String> MODULE_DIRS = List.of("Модули", "Modules");
  /** Дополнительный префикс для конвенциональных каталогов: {@code src/Классы}, {@code src/Modules}, … */
  static final String SRC_PREFIX = "src";

  /** Расширение OneScript-файлов. */
  public static final String OS_SUFFIX = ".os";
  private static final int MAX_DEPTH = 6;

  private final LibConfigDiscovery libConfigDiscovery;

  /**
   * Описание convention-based библиотеки.
   *
   * @param root         корневой каталог библиотеки
   * @param classFiles   .os-файлы, найденные в {@code Классы}/{@code Classes}
   * @param moduleFiles  .os-файлы, найденные в {@code Модули}/{@code Modules}
   */
  public record ConventionalLibrary(Path root, List<Path> classFiles, List<Path> moduleFiles) {
  }

  /**
   * Найти все convention-based библиотеки указанного workspace.
   *
   * @param serverContext       workspace-контекст
   * @param libConfigManifests  пути к уже найденным {@code lib.config}; их
   *                            каталоги исключаются из конвенционального обхода
   */
  public List<ConventionalLibrary> discover(ServerContext serverContext, Collection<Path> libConfigManifests) {
    var skip = new HashSet<Path>();
    for (var manifest : libConfigManifests) {
      var parent = manifest.getParent();
      if (parent != null) {
        skip.add(parent.toAbsolutePath().normalize());
      }
    }

    var result = new ArrayList<ConventionalLibrary>();
    var visited = new HashSet<Path>();
    for (var root : libConfigDiscovery.getRoots(serverContext)) {
      collectFromRoot(root, skip, visited, result);
    }
    return result;
  }

  private void collectFromRoot(Path root, Set<Path> skip, Set<Path> visited, List<ConventionalLibrary> sink) {
    if (!Files.isDirectory(root)) {
      return;
    }
    walk(root, skip, visited, sink, 0);
  }

  private static void walk(Path dir, Set<Path> skip, Set<Path> visited,
                           List<ConventionalLibrary> sink, int depth) {
    var normalized = dir.toAbsolutePath().normalize();
    if (!visited.add(normalized) || skip.contains(normalized)) {
      return;
    }
    // Сильный сигнал: convention-каталоги Классы/Модули. Это завершённая
    // библиотека — внутрь не спускаемся.
    if (tryRegisterConventional(normalized, sink)) {
      return;
    }
    // Слабый сигнал (третий способ подключения): плоские .os прямо в каталоге.
    // Регистрируем их как flat-библиотеку, но на корневом уровне (depth == 0)
    // НЕ прекращаем обход: рядом с потребляющим скриптом (плоский .os в корне
    // workspace) может лежать каталог-библиотека, подключаемая относительным
    // путём #Использовать "<dir>". На вложенных уровнях flat-каталог по-прежнему
    // считаем завершённой библиотекой и внутрь не спускаемся.
    var registeredFlat = tryRegisterFlat(normalized, sink);
    if (registeredFlat && depth > 0) {
      return;
    }
    if (depth >= MAX_DEPTH) {
      return;
    }
    try (var stream = Files.list(dir)) {
      stream
        .filter(Files::isDirectory)
        // Не заходим в oscript_modules уже обходимого каталога — транзитивные
        // зависимости не должны переоткрываться convention-discovery'ем как
        // отдельные библиотеки. Корневой workspace/oscript_modules/<lib>
        // обрабатывается отдельно через addOscriptModulesChildren.
        .filter(child -> !LibConfigDiscovery.OSCRIPT_MODULES_DIRNAME.equals(child.getFileName().toString()))
        // Если каталог зарегистрирован как flat-библиотека, его подкаталог src
        // уже включён в неё (listFlatOsFiles читает и <root>/src) — повторно как
        // отдельную библиотеку не открываем.
        .filter(child -> !(registeredFlat && SRC_PREFIX.equals(child.getFileName().toString())))
        .forEach(child -> walk(child, skip, visited, sink, depth + 1));
    } catch (IOException e) {
      LOGGER.debug("Skipping unreadable directory while scanning conventional libraries: {}", dir, e);
    }
  }

  /** Регистрация по convention-каталогам {@code Классы}/{@code Модули} (сильный сигнал). */
  private static boolean tryRegisterConventional(Path libraryRoot, List<ConventionalLibrary> sink) {
    var classFiles = listOsFiles(libraryRoot, CLASS_DIRS);
    var moduleFiles = listOsFiles(libraryRoot, MODULE_DIRS);
    if (!classFiles.isEmpty() || !moduleFiles.isEmpty()) {
      sink.add(new ConventionalLibrary(libraryRoot, classFiles, moduleFiles));
      return true;
    }
    return false;
  }

  /**
   * Третий способ подключения библиотеки: каталог без {@code lib.config} и без
   * {@code Классы}/{@code Модули}, но содержащий {@code .os}-файлы — все они
   * подключаются как модули.
   */
  private static boolean tryRegisterFlat(Path libraryRoot, List<ConventionalLibrary> sink) {
    var flatModules = listFlatOsFiles(libraryRoot);
    if (!flatModules.isEmpty()) {
      sink.add(new ConventionalLibrary(libraryRoot, List.of(), flatModules));
      return true;
    }
    return false;
  }

  private static List<Path> listFlatOsFiles(Path libraryRoot) {
    var result = new LinkedHashSet<Path>();
    addOsFilesFrom(libraryRoot, result);
    addOsFilesFrom(libraryRoot.resolve(SRC_PREFIX), result);
    return result.isEmpty() ? List.of() : List.copyOf(result);
  }

  private static List<Path> listOsFiles(Path libraryRoot, List<String> dirNames) {
    var result = new LinkedHashSet<Path>();
    for (var dirName : dirNames) {
      addOsFilesFrom(libraryRoot.resolve(dirName), result);
      addOsFilesFrom(libraryRoot.resolve(SRC_PREFIX).resolve(dirName), result);
    }
    return result.isEmpty() ? List.of() : List.copyOf(result);
  }

  private static void addOsFilesFrom(Path dir, Set<Path> sink) {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> stream = Files.list(dir)) {
      stream
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(OS_SUFFIX))
        .forEach(sink::add);
    } catch (IOException e) {
      LOGGER.debug("Skipping unreadable conventional library directory: {}", dir, e);
    }
  }

  /**
   * Имя класса/модуля из {@code .os}-файла — basename без расширения.
   */
  public static String entryName(Path osFile) {
    var fileName = osFile.getFileName().toString();
    var dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  /**
   * Возвращает имена каталогов для классов (для тестов/документации).
   */
  public static List<String> classDirNames() {
    return Collections.unmodifiableList(CLASS_DIRS);
  }

  /**
   * Возвращает имена каталогов для модулей (для тестов/документации).
   */
  public static List<String> moduleDirNames() {
    return Collections.unmodifiableList(MODULE_DIRS);
  }
}
