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
package com.github._1c_syntax.bsl.languageserver.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Поиск BSL/OS файлов внутри каталога с учётом исключений.
 * Каталоги под {@code excludePaths} не разворачиваются — обход обрезается
 * на уровне directory-фильтра.
 */
@UtilityClass
public class BSLFiles {

  private static final String[] BSL_EXTENSIONS = {"bsl", "os"};
  private static final IOFileFilter BSL_FILE_FILTER = new SuffixFileFilter(BSL_EXTENSIONS);

  /**
   * Возвращает все BSL/OS файлы внутри {@code srcDir}, не попадающие под {@code excludePaths}.
   * Каталоги, совпадающие с паттернами исключения, не разворачиваются.
   *
   * @param srcDir       корневой каталог поиска
   * @param excludePaths паттерны исключения (см. {@link PathExclusionUtils}); {@code null} или пустой
   *                     список означает «без фильтрации»
   * @return найденные файлы; пустая коллекция, если ничего не найдено
   */
  public static Collection<File> listBslFiles(Path srcDir, @Nullable List<String> excludePaths) {
    var collector = new BslFileCollector(srcDir, PathExclusionUtils.filters(excludePaths));
    try {
      // Атрибуты файла приходят из самого обхода. Фильтр FileUtils.listFiles спрашивал их
      // у файловой системы заново, на каждый файл и до проверки расширения, — а в выгрузке
      // конфигурации модулей лишь малая часть файлов.
      Files.walkFileTree(srcDir, collector);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return collector.files;
  }

  /**
   * Обход каталога, собирающий BSL/OS файлы и не заходящий в исключённые каталоги.
   */
  private static final class BslFileCollector extends SimpleFileVisitor<Path> {

    private final Path root;
    private final PathExclusionUtils.ExclusionFilters exclusions;
    private final List<File> files = new ArrayList<>();

    private BslFileCollector(Path root, PathExclusionUtils.ExclusionFilters exclusions) {
      this.root = root;
      this.exclusions = exclusions;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
      if (!dir.equals(root) && !accepts(exclusions.directoryFilter(), dir, attributes)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
      if (accepts(BSL_FILE_FILTER, file, attributes)
        && isRegularFile(file, attributes)
        && accepts(exclusions.fileFilter(), file, attributes)) {
        files.add(file.toFile());
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      // Корня нет, файл удалён посреди обхода либо каталог не читается — модулей там не
      // найти, а остальные искать надо. Так же поступал и FileUtils.listFiles.
      return FileVisitResult.CONTINUE;
    }
  }

  private static boolean accepts(IOFileFilter filter, Path path, BasicFileAttributes attributes) {
    return filter.accept(path, attributes) == FileVisitResult.CONTINUE;
  }

  private static boolean isRegularFile(Path file, BasicFileAttributes attributes) {
    // Обход по ссылкам не идёт, и атрибуты ссылки — её собственные; на что она указывает,
    // приходится спросить отдельно.
    return attributes.isRegularFile() || (attributes.isSymbolicLink() && Files.isRegularFile(file));
  }
}
