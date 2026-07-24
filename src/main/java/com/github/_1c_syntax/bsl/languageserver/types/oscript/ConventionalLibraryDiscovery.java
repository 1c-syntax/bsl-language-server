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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
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
   * Результат единого обхода дерева workspace: найденные {@code lib.config}-манифесты
   * и convention/flat-библиотеки.
   *
   * @param libConfigs             абсолютные нормализованные пути к {@code lib.config}
   * @param conventionalLibraries  convention/flat-библиотеки (без тех, что управляются
   *                               {@code lib.config})
   */
  public record DiscoveryResult(List<Path> libConfigs, List<ConventionalLibrary> conventionalLibraries) {
  }

  /**
   * Найти за ОДИН обход дерева workspace и {@code lib.config}-манифесты, и
   * convention/flat-библиотеки.
   * <p>
   * Раньше это были два независимых полных обхода (отдельный поиск {@code lib.config}
   * и отдельный convention-обход); на больших конфигурациях каждый стоил секунды.
   * Здесь дерево обходится однократно: каждый каталог читается один раз, а
   * {@code lib.config} и convention-каталоги определяются из того же листинга.
   * <p>
   * Семантика сохранена относительно прежней пары обходов:
   * <ul>
   *   <li>{@code lib.config} собираются на всю глубину {@value #MAX_DEPTH} и НЕ
   *       прерывают спуск (в т.ч. находятся вложенные под уже обнаруженной
   *       библиотекой);</li>
   *   <li>каталог с {@code lib.config} и всё его поддерево исключаются из
   *       convention/flat-регистрации (библиотеку индексирует
   *       {@link LibConfigDiscovery}/{@link LibConfigParser});</li>
   *   <li>convention-каталог ({@code Классы}/{@code Модули}) и flat-каталог на
   *       вложенном уровне — завершённая библиотека: глубже как отдельные
   *       библиотеки не регистрируются (но спуск продолжается ради вложенных
   *       {@code lib.config});</li>
   *   <li>{@code oscript_modules} пропускается на любой глубине.</li>
   * </ul>
   *
   * @param serverContext workspace-контекст
   * @return манифесты и convention/flat-библиотеки
   */
  public DiscoveryResult discoverAll(ServerContext serverContext) {
    var libConfigs = new LinkedHashSet<Path>();
    var conventional = new ArrayList<ConventionalLibrary>();
    var visited = new HashSet<Path>();
    for (var root : libConfigDiscovery.getRoots(serverContext)) {
      if (Files.isDirectory(root)) {
        walk(root, visited, libConfigs, conventional, 0, false);
      }
    }
    return new DiscoveryResult(new ArrayList<>(libConfigs), conventional);
  }

  /**
   * Содержимое каталога, прочитанное за один проход: непосредственные
   * подкаталоги (по имени), {@code .os}-файлы в самом каталоге и {@code lib.config}
   * (если есть). Один каталог читается ровно один раз, поэтому обход больше не
   * делает ни повторных листингов, ни слепых {@code isDirectory}-проб по
   * несуществующим {@code Классы}/{@code Модули}/{@code src} — всё выводится из
   * этого листинга.
   */
  private record DirContents(Map<String, Path> subdirs, List<Path> osFiles, @Nullable Path libConfig) {
    static final DirContents EMPTY = new DirContents(Map.of(), List.of(), null);
  }

  private static DirContents readDir(Path dir) {
    var subdirs = new LinkedHashMap<String, Path>();
    var osFiles = new ArrayList<Path>();
    Path libConfig = null;
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
        } else if (attrs.isRegularFile()) {
          var name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
          if (libConfig == null && LibConfigDiscovery.LIB_CONFIG_FILENAME.equals(name)) {
            libConfig = entry.toAbsolutePath().normalize();
          } else if (name.endsWith(OS_SUFFIX)) {
            osFiles.add(entry);
          }
        }
      }
    } catch (IOException e) {
      LOGGER.debug("Skipping unreadable directory while scanning conventional libraries: {}", dir, e);
      return DirContents.EMPTY;
    }
    return new DirContents(subdirs, osFiles, libConfig);
  }

  /**
   * Единый обход. {@code suppressed} означает, что текущее поддерево уже
   * «занято» (лежит под {@code lib.config}-каталогом или под уже
   * зарегистрированной convention/flat-библиотекой): convention/flat-регистрация
   * здесь не выполняется, но спуск продолжается ради вложенных {@code lib.config}.
   */
  private static void walk(Path dir, Set<Path> visited, Set<Path> libConfigs,
                           List<ConventionalLibrary> conventional, int depth, boolean suppressed) {
    var normalized = dir.toAbsolutePath().normalize();
    if (!visited.add(normalized)) {
      return;
    }

    var contents = readDir(normalized);
    // lib.config собирается на всю глубину MAX_DEPTH (файл лежит на уровне depth+1).
    if (contents.libConfig() != null && depth < MAX_DEPTH) {
      libConfigs.add(contents.libConfig());
    }

    // Каталог с lib.config (и всё его поддерево) не участвует в convention/flat —
    // его индексирует lib.config-путь.
    var conventionallySuppressed = suppressed || contents.libConfig() != null;
    var childSuppressed = conventionallySuppressed;
    var flatAtRoot = false;

    if (!conventionallySuppressed) {
      // src читаем один раз: он нужен и для convention-каталогов (src/Классы), и
      // для flat-скриптов (плоские .os в src).
      var srcPath = contents.subdirs().get(SRC_PREFIX);
      var srcContents = srcPath == null ? DirContents.EMPTY : readDir(srcPath);

      // Сильный сигнал: convention-каталоги Классы/Модули (в т.ч. под src). Это
      // завершённая библиотека — глубже как отдельные библиотеки не регистрируем.
      var classFiles = collectConventionalOsFiles(contents, srcContents, CLASS_DIRS);
      var moduleFiles = collectConventionalOsFiles(contents, srcContents, MODULE_DIRS);
      if (!classFiles.isEmpty() || !moduleFiles.isEmpty()) {
        conventional.add(new ConventionalLibrary(normalized, classFiles, moduleFiles));
        childSuppressed = true;
      } else {
        // Слабый сигнал (третий способ подключения): плоские .os прямо в каталоге
        // (и в его src). Регистрируем как flat-библиотеку, но на корневом уровне
        // (depth == 0) НЕ прекращаем обход: рядом с потребляющим скриптом (плоский
        // .os в корне workspace) может лежать каталог-библиотека, подключаемая
        // относительным путём #Использовать "<dir>". На вложенных уровнях flat-каталог
        // считаем завершённой библиотекой.
        var flatModules = new LinkedHashSet<Path>();
        flatModules.addAll(contents.osFiles());
        flatModules.addAll(srcContents.osFiles());
        if (!flatModules.isEmpty()) {
          conventional.add(new ConventionalLibrary(normalized, List.of(), List.copyOf(flatModules)));
          if (depth > 0) {
            childSuppressed = true;
          } else {
            flatAtRoot = true;
          }
        }
      }
    }

    if (depth >= MAX_DEPTH) {
      return;
    }

    for (var child : contents.subdirs().entrySet()) {
      var name = child.getKey();
      // Не заходим в oscript_modules уже обходимого каталога — транзитивные
      // зависимости не должны переоткрываться как отдельные библиотеки. Корневой
      // workspace/oscript_modules/<lib> обрабатывается отдельно через
      // addOscriptModulesChildren (как отдельный корень).
      if (LibConfigDiscovery.OSCRIPT_MODULES_DIRNAME.equals(name)) {
        continue;
      }
      // src flat-каталога уровня 0 уже включён в flat-библиотеку — как отдельную
      // библиотеку его не регистрируем, но спускаемся ради вложенных lib.config.
      var childSup = childSuppressed || (flatAtRoot && SRC_PREFIX.equals(name));
      walk(child.getValue(), visited, libConfigs, conventional, depth + 1, childSup);
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
