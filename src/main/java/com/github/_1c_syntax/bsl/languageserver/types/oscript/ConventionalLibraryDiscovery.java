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

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Распознавание OneScript-библиотеки по соглашению об именовании каталогов —
 * для ОДНОГО каталога, без обхода дерева.
 * <p>
 * По содержимому каталога (см. {@link DirContents}) решает, является ли он
 * библиотекой:
 * <ul>
 *   <li>convention — есть подкаталоги {@code Классы}/{@code Classes} и/или
 *       {@code Модули}/{@code Modules} (в т.ч. под {@code src}) с {@code .os}-файлами;</li>
 *   <li>flat — {@code .os}-файлы лежат прямо в каталоге (и/или в его {@code src}),
 *       все они подключаются как модули.</li>
 * </ul>
 * Обход дерева и решение, какие каталоги вообще проверять (например, пропуск
 * поддеревьев с {@code lib.config}), — забота {@link OScriptLibraryScanner};
 * этот компонент про {@code lib.config} ничего не знает.
 */
@Component
public class ConventionalLibraryDiscovery {

  /** Имена convention-каталогов для классов. */
  public static final List<String> CLASS_DIRS = List.of("Классы", "Classes");
  /** Имена convention-каталогов для модулей. */
  public static final List<String> MODULE_DIRS = List.of("Модули", "Modules");
  /** Дополнительный префикс для конвенциональных каталогов: {@code src/Классы}, {@code src/Modules}, … */
  static final String SRC_PREFIX = "src";

  /** Вид распознанной библиотеки. */
  public enum Kind {
    /** Не библиотека. */
    NONE,
    /** convention: {@code Классы}/{@code Модули}. */
    CONVENTION,
    /** flat: плоские {@code .os}. */
    FLAT
  }

  /**
   * Результат распознавания одного каталога.
   *
   * @param kind    вид библиотеки
   * @param library описание библиотеки; {@code null}, если {@code kind == NONE}
   */
  public record Classification(Kind kind, @Nullable ConventionalLibrary library) {
    static final Classification NONE = new Classification(Kind.NONE, null);
  }

  /**
   * Описание convention/flat-библиотеки.
   *
   * @param root         корневой каталог библиотеки
   * @param classFiles   .os-файлы, найденные в {@code Классы}/{@code Classes}
   * @param moduleFiles  .os-файлы, найденные в {@code Модули}/{@code Modules} (или flat)
   */
  public record ConventionalLibrary(Path root, List<Path> classFiles, List<Path> moduleFiles) {
  }

  /**
   * Распознать один каталог как convention/flat-библиотеку.
   *
   * @param dir      каталог (абсолютный, нормализованный)
   * @param contents его уже прочитанное содержимое
   * @return результат распознавания ({@link Kind#NONE}, если не библиотека)
   */
  public Classification classify(Path dir, DirContents contents) {
    // src читаем один раз: он нужен и для convention-каталогов (src/Классы), и
    // для flat-скриптов (плоские .os в src).
    var srcPath = contents.subdirs().get(SRC_PREFIX);
    var srcContents = srcPath == null ? DirContents.EMPTY : DirContents.read(srcPath);

    // Сильный сигнал: convention-каталоги Классы/Модули (в т.ч. под src).
    var classFiles = collectConventionalOsFiles(contents, srcContents, CLASS_DIRS);
    var moduleFiles = collectConventionalOsFiles(contents, srcContents, MODULE_DIRS);
    if (!classFiles.isEmpty() || !moduleFiles.isEmpty()) {
      return new Classification(Kind.CONVENTION, new ConventionalLibrary(dir, classFiles, moduleFiles));
    }

    // Слабый сигнал (третий способ подключения): плоские .os прямо в каталоге и в его src.
    var flatModules = new LinkedHashSet<Path>();
    flatModules.addAll(contents.osFiles());
    flatModules.addAll(srcContents.osFiles());
    if (!flatModules.isEmpty()) {
      return new Classification(Kind.FLAT, new ConventionalLibrary(dir, List.of(), List.copyOf(flatModules)));
    }

    return Classification.NONE;
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
        result.addAll(DirContents.read(conventionDir).osFiles());
      }
      var srcConventionDir = srcContents.subdirs().get(dirName);
      if (srcConventionDir != null) {
        result.addAll(DirContents.read(srcConventionDir).osFiles());
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
