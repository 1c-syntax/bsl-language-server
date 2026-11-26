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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Примитив «чтения каталога»: содержимое одного каталога, прочитанное за
 * ОДИН листинг — непосредственные подкаталоги (по имени), {@code .os}-файлы и
 * {@code lib.config} (если есть).
 * <p>
 * Единая точка обращения к файловой системе на уровне одного каталога: и обход
 * дерева ({@link OScriptLibraryScanner}), и распознавание convention-каталогов
 * ({@link ConventionalLibraryDiscovery}) берут содержимое отсюда, поэтому один
 * каталог читается ровно один раз, без повторных листингов и слепых
 * {@code isDirectory}-проб по несуществующим {@code Классы}/{@code Модули}/{@code src}.
 *
 * @param subdirs   непосредственные подкаталоги: имя → путь
 * @param osFiles   {@code .os}-файлы непосредственно в каталоге
 * @param libConfig путь к {@code lib.config}, если он есть в каталоге, иначе {@code null}
 */
record DirContents(Map<String, Path> subdirs, List<Path> osFiles, @Nullable Path libConfig) {

  /** Имя файла-манифеста OneScript-библиотеки. */
  static final String LIB_CONFIG_FILENAME = "lib.config";
  /** Расширение OneScript-файлов. */
  static final String OS_SUFFIX = ".os";

  /** Пустое содержимое (для отсутствующих/нечитаемых каталогов). */
  static final DirContents EMPTY = new DirContents(Map.of(), List.of(), null);

  private static final Logger LOGGER = LoggerFactory.getLogger(DirContents.class);

  /**
   * Прочитать каталог за один листинг.
   *
   * @param dir каталог
   * @return его содержимое; {@link #EMPTY}, если каталог нечитаем
   */
  static DirContents read(Path dir) {
    var subdirs = new LinkedHashMap<String, Path>();
    var osFiles = new ArrayList<Path>();
    Path libConfig = null;
    try (var stream = Files.newDirectoryStream(dir)) {
      for (var entry : stream) {
        BasicFileAttributes attrs;
        try {
          attrs = Files.readAttributes(entry, BasicFileAttributes.class);
        } catch (IOException e) {
          LOGGER.debug("Skipping unreadable entry while scanning oscript libraries: {}", entry, e);
          continue;
        }
        if (attrs.isDirectory()) {
          subdirs.put(entry.getFileName().toString(), entry);
        } else if (attrs.isRegularFile()) {
          var name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
          if (libConfig == null && LIB_CONFIG_FILENAME.equals(name)) {
            libConfig = entry.toAbsolutePath().normalize();
          } else if (name.endsWith(OS_SUFFIX)) {
            osFiles.add(entry);
          }
        }
      }
    } catch (IOException e) {
      LOGGER.debug("Skipping unreadable directory while scanning oscript libraries: {}", dir, e);
      return EMPTY;
    }
    return new DirContents(subdirs, osFiles, libConfig);
  }
}
