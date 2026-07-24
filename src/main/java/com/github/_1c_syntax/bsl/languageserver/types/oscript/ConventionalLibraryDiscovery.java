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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

  /**
   * Содержимое каталога, прочитанное за один проход: непосредственные
   * подкаталоги (по имени) и {@code .os}-файлы в самом каталоге. Один каталог
   * читается ровно один раз, поэтому convention-обход больше не делает ни
   * повторных листингов, ни слепых {@code isDirectory}-проб по несуществующим
   * {@code Классы}/{@code Модули}/{@code src} — они выводятся из этого листинга.
   */
  private record DirContents(Map<String, Path> subdirs, List<Path> osFiles) {
    static final DirContents EMPTY = new DirContents(Map.of(), List.of());
  }

  private static DirContents readDir(Path dir) {
    var subdirs = new LinkedHashMap<String, Path>();
    var osFiles = new ArrayList<Path>();
    try (var stream = Files.newDirectoryStream(dir)) {
      for (var entry : stream) {
        BasicFileAttributes attrs;
        try {
          attrs = Files.readAttributes(entry, BasicFileAttributes.class);
        } catch (IOException e) {
          LOGGER.debug("Skipping unreadable entry while scanning conventional libraries: {}", entry, e);
          continue;
        }
        if (attrs.isDirectory()) {
          subdirs.put(entry.getFileName().toString(), entry);
        } else if (attrs.isRegularFile()
          && entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(OS_SUFFIX)) {
          osFiles.add(entry);
        }
      }
    } catch (IOException e) {
      LOGGER.debug("Skipping unreadable directory while scanning conventional libraries: {}", dir, e);
      return DirContents.EMPTY;
    }
    return new DirContents(subdirs, osFiles);
  }

  private static void walk(Path dir, Set<Path> skip, Set<Path> visited,
                           List<ConventionalLibrary> sink, int depth) {
    var normalized = dir.toAbsolutePath().normalize();
    if (!visited.add(normalized) || skip.contains(normalized)) {
      return;
    }

    var contents = readDir(normalized);
    // src читаем один раз: он нужен и для convention-каталогов (src/Классы), и
    // для flat-скриптов (плоские .os в src).
    var srcPath = contents.subdirs().get(SRC_PREFIX);
    var srcContents = srcPath == null ? DirContents.EMPTY : readDir(srcPath);

    // Сильный сигнал: convention-каталоги Классы/Модули (в т.ч. под src). Это
    // завершённая библиотека — внутрь не спускаемся.
    var classFiles = collectConventionalOsFiles(contents, srcContents, CLASS_DIRS);
    var moduleFiles = collectConventionalOsFiles(contents, srcContents, MODULE_DIRS);
    if (!classFiles.isEmpty() || !moduleFiles.isEmpty()) {
      sink.add(new ConventionalLibrary(normalized, classFiles, moduleFiles));
      return;
    }

    // Слабый сигнал (третий способ подключения): плоские .os прямо в каталоге
    // (и в его src). Регистрируем их как flat-библиотеку, но на корневом уровне
    // (depth == 0) НЕ прекращаем обход: рядом с потребляющим скриптом (плоский
    // .os в корне workspace) может лежать каталог-библиотека, подключаемая
    // относительным путём #Использовать "<dir>". На вложенных уровнях flat-каталог
    // по-прежнему считаем завершённой библиотекой и внутрь не спускаемся.
    var flatModules = new LinkedHashSet<Path>();
    flatModules.addAll(contents.osFiles());
    flatModules.addAll(srcContents.osFiles());
    var registeredFlat = !flatModules.isEmpty();
    if (registeredFlat) {
      sink.add(new ConventionalLibrary(normalized, List.of(), List.copyOf(flatModules)));
      if (depth > 0) {
        return;
      }
    }

    if (depth >= MAX_DEPTH) {
      return;
    }

    for (var child : contents.subdirs().entrySet()) {
      var name = child.getKey();
      // Не заходим в oscript_modules уже обходимого каталога — транзитивные
      // зависимости не должны переоткрываться convention-discovery'ем как
      // отдельные библиотеки. Корневой workspace/oscript_modules/<lib>
      // обрабатывается отдельно через addOscriptModulesChildren.
      if (LibConfigDiscovery.OSCRIPT_MODULES_DIRNAME.equals(name)) {
        continue;
      }
      // Если каталог зарегистрирован как flat-библиотека, его подкаталог src уже
      // включён в неё (плоские .os из src) — повторно не открываем.
      if (registeredFlat && SRC_PREFIX.equals(name)) {
        continue;
      }
      walk(child.getValue(), skip, visited, sink, depth + 1);
    }
  }

  /**
   * .os-файлы convention-каталогов {@code dirNames} (например,
   * {@code Классы}/{@code Classes}) — как в самом каталоге библиотеки, так и под
   * его {@code src}. Порядок и отсутствие дубликатов сохраняются
   * ({@link LinkedHashSet}); каталог читается лишь если реально присутствует в
   * заранее прочитанном листинге.
   */
  private static List<Path> collectConventionalOsFiles(DirContents contents, DirContents srcContents,
                                                       List<String> dirNames) {
    var result = new LinkedHashSet<Path>();
    for (var dirName : dirNames) {
      var conventionDir = contents.subdirs().get(dirName);
      if (conventionDir != null) {
        result.addAll(readDir(conventionDir).osFiles());
      }
      var srcConventionDir = srcContents.subdirs().get(dirName);
      if (srcConventionDir != null) {
        result.addAll(readDir(srcConventionDir).osFiles());
      }
    }
    return result.isEmpty() ? List.of() : List.copyOf(result);
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
