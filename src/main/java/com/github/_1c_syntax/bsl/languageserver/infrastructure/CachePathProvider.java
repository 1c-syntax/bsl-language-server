/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

/**
 * Компонент для определения пути к персистентному кэшу ehcache.
 * <p>
 * Кэш размещается в каталоге пользователя в подкаталоге, имя которого вычисляется
 * на основе MD5-хэша текущей рабочей директории. Это позволяет избежать конфликтов
 * при работе с разными проектами и не захламлять git-репозитории.
 */
@Component
public class CachePathProvider {

  private static final String CACHE_BASE_DIR = ".bsl-language-server";
  private static final String CACHE_SUBDIR = "cache";

  /**
   * Возвращает путь к каталогу персистентного кэша для текущей рабочей директории.
   * <p>
   * Если fullPath не пустой, возвращает его напрямую.
   * Иначе формирует путь по шаблону: {@code ${basePath}/.bsl-language-server/cache/<md5-hash>/}
   * где md5-hash - это MD5-хэш абсолютного пути текущей рабочей директории.
   *
   * @param basePath базовый путь к каталогу (обычно user.home)
   * @param fullPath полный путь к каталогу кэша (если задан, используется напрямую)
   * @return путь к каталогу кэша
   */
  public Path getCachePath(String basePath, String fullPath) {
    return getCachePath(basePath, fullPath, 0);
  }

  /**
   * Возвращает путь к каталогу персистентного кэша для текущей рабочей директории с учётом номера экземпляра.
   * <p>
   * Если fullPath не пустой, возвращает его напрямую (instanceNumber игнорируется).
   * Иначе формирует путь по шаблону:
   * <ul>
   *   <li>При instanceNumber = 0: {@code ${basePath}/.bsl-language-server/cache/<md5-hash>/}</li>
   *   <li>При instanceNumber > 0: {@code ${basePath}/.bsl-language-server/cache/<md5-hash>@<instanceNumber>/}</li>
   * </ul>
   * где md5-hash - это MD5-хэш абсолютного пути текущей рабочей директории.
   * <p>
   * Суффикс с номером экземпляра позволяет нескольким экземплярам BSL LS работать в одной директории
   * с отдельными персистентными кэшами.
   *
   * @param basePath базовый путь к каталогу (обычно user.home)
   * @param fullPath полный путь к каталогу кэша (если задан, используется напрямую)
   * @param instanceNumber номер экземпляра (0 для основного, 1+ для дополнительных)
   * @return путь к каталогу кэша
   */
  public Path getCachePath(String basePath, String fullPath, int instanceNumber) {
    if (fullPath != null && !fullPath.isEmpty()) {
      return Path.of(fullPath);
    }
    
    var currentDir = getCurrentDirectory();
    var hash = md5Hex(currentDir);
    
    // Add instance suffix for additional instances
    var cacheDirName = instanceNumber > 0 ? hash + "@" + instanceNumber : hash;
    
    return Path.of(basePath, CACHE_BASE_DIR, CACHE_SUBDIR, cacheDirName);
  }

  /**
   * Получает абсолютный путь к текущей рабочей директории.
   *
   * @return абсолютный путь к текущей директории
   */
  private String getCurrentDirectory() {
    try {
      return Path.of(".").toRealPath().toString();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to resolve current directory", e);
    }
  }
}
