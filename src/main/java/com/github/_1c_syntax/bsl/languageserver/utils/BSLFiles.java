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

import com.github._1c_syntax.utils.Absolute;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
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
    var normalizedSrcDir = Absolute.path(srcDir);
    var exclusions = PathExclusionUtils.filters(excludePaths);
    var fileFilter = BSL_FILE_FILTER.and(exclusions.fileFilter());

    return FileUtils.listFiles(normalizedSrcDir.toFile(), fileFilter, exclusions.directoryFilter());
  }
}
