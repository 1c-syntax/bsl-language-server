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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Поиск BSL/OS файлов внутри каталога с учётом списка путей-исключений.
 * <p>
 * Фильтрация применяется ещё на этапе обхода — каталоги, попадающие
 * под {@code excludePaths}, не разворачиваются. Это даёт заметный выигрыш
 * на больших проектах по сравнению с пост-фильтрацией уже собранного списка.
 */
@UtilityClass
public class BSLFiles {

  private static final String[] BSL_EXTENSIONS = {"bsl", "os"};
  private static final IOFileFilter BSL_FILE_FILTER = new SuffixFileFilter(BSL_EXTENSIONS);

  /**
   * Возвращает список BSL/OS файлов внутри {@code srcDir} с учётом исключений.
   *
   * @param srcDir       каталог поиска
   * @param excludeRoot  корень, относительно которого вычисляются пути для сопоставления с шаблонами;
   *                     если {@code null}, фильтрация исключений не применяется
   * @param excludePaths паттерны исключения; если {@code null} или пуст — фильтрация не применяется
   * @return найденные файлы
   */
  public static List<File> listBslFiles(
    Path srcDir,
    @Nullable Path excludeRoot,
    @Nullable List<String> excludePaths
  ) {
    var hasExclusions = excludeRoot != null && excludePaths != null && !excludePaths.isEmpty();
    IOFileFilter fileFilter;
    IOFileFilter directoryFilter;
    if (hasExclusions) {
      var notExcluded = notExcludedFilter(excludeRoot, excludePaths);
      fileFilter = BSL_FILE_FILTER.and(notExcluded);
      directoryFilter = notExcluded;
    } else {
      fileFilter = BSL_FILE_FILTER;
      directoryFilter = TrueFileFilter.INSTANCE;
    }

    Collection<File> found = FileUtils.listFiles(srcDir.toFile(), fileFilter, directoryFilter);
    return List.copyOf(found);
  }

  private static IOFileFilter notExcludedFilter(Path excludeRoot, List<String> excludePaths) {
    return new IOFileFilter() {
      @Override
      public boolean accept(File file) {
        return !PathExclusionUtils.isExcluded(excludeRoot, file.toPath(), excludePaths);
      }

      @Override
      public boolean accept(File dir, String name) {
        return accept(new File(dir, name));
      }
    };
  }
}
