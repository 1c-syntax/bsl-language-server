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

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Утилита для определения пути к персистентному кэшу ehcache.
 * <p>
 * Кэш размещается в каталоге пользователя в подкаталоге, имя которого вычисляется
 * на основе MD5-хэша текущей рабочей директории. Это позволяет избежать конфликтов
 * при работе с разными проектами и не захламлять git-репозитории.
 */
@UtilityClass
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
  public static Path getCachePath(String basePath, String fullPath) {
    if (fullPath != null && !fullPath.isEmpty()) {
      return Path.of(fullPath);
    }
    
    var currentDir = System.getProperty("user.dir");
    var hash = computeHash(currentDir);
    
    return Path.of(basePath, CACHE_BASE_DIR, CACHE_SUBDIR, hash);
  }

  /**
   * Вычисляет MD5-хэш строки и возвращает его в виде шестнадцатеричного представления.
   *
   * @param input строка для хэширования
   * @return MD5-хэш в виде hex-строки
   */
  private static String computeHash(String input) {
    try {
      var digest = MessageDigest.getInstance("MD5");
      var hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      // MD5 всегда доступен в стандартной JVM
      throw new IllegalStateException("MD5 algorithm not available", e);
    }
  }

  /**
   * Конвертирует массив байтов в шестнадцатеричную строку.
   *
   * @param bytes массив байтов
   * @return hex-строка
   */
  private static String bytesToHex(byte[] bytes) {
    var hexString = new StringBuilder(2 * bytes.length);
    for (byte b : bytes) {
      var hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }
}
