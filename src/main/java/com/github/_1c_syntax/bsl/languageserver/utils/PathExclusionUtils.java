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
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

/**
 * Утилита проверки исключения путей по списку паттернов.
 * <p>
 * Поддерживаются простые имена каталогов (например, ".git", "node_modules")
 * и glob-паттерны. Простые имена трактуются как сегмент пути (каталог).
 */
@Slf4j
@UtilityClass
public class PathExclusionUtils {

  /**
   * Проверяет, исключён ли путь из индексации по списку паттернов.
   * <p>
   * Путь приводится к относительному от {@code root}. Если путь не является подпутём root,
   * возвращается false. Для простых имён (без «/» и «*») проверяется вхождение сегмента;
   * для glob-паттернов используется PathMatcher.
   *
   * @param root     корень, относительно которого проверяется путь; может быть null
   * @param path     абсолютный путь к файлу или каталогу
   * @param patterns список паттернов (имена каталогов или glob); может быть null
   * @return true, если путь совпадает с одним из паттернов
   */
  public static boolean isExcluded(@Nullable Path root, Path path, @Nullable List<String> patterns) {
    if (root == null || patterns == null || patterns.isEmpty()) {
      return false;
    }

    var normalizedRoot = Absolute.path(root);
    var normalizedPath = Absolute.path(path);
    if (!normalizedPath.startsWith(normalizedRoot)) {
      return false;
    }

    var relativePath = normalizedRoot.relativize(normalizedPath);
    for (var pattern : patterns) {
      if (pattern == null || pattern.isBlank()) {
        continue;
      }
      if (matchesPattern(relativePath, pattern.trim())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Сопоставляет относительный путь с паттерном: для простых имён — по сегментам,
   * для glob — через PathMatcher (путь нормализуется к «/»).
   * Паттерн нормализуется: обратный слэш заменяется на «/» для кроссплатформенности.
   */
  private static boolean matchesPattern(Path relativePath, String pattern) {
    var normalized = pattern.replace('\\', '/');
    if (!normalized.contains("/") && !normalized.contains("*")) {
      for (var i = 0; i < relativePath.getNameCount(); i++) {
        if (relativePath.getName(i).toString().equals(normalized)) {
          return true;
        }
      }
      return false;
    }
    try {
      var matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalized);
      return matcher.matches(pathForGlobMatching(relativePath));
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Некорректный glob-паттерн исключения, пропуск: {}", pattern, e);
      return false;
    }
  }

  /** Собирает путь из сегментов с разделителем «/» для кроссплатформенного glob. */
  private static Path pathForGlobMatching(Path path) {
    if (path.getNameCount() == 0) {
      return path;
    }
    var names = new String[path.getNameCount()];
    for (var i = 0; i < path.getNameCount(); i++) {
      names[i] = path.getName(i).toString();
    }
    return path.getFileSystem().getPath(String.join("/", names));
  }
}
